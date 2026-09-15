locals {
  name = "${var.project_name}-${var.environment}"

  common_tags = merge({
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
  }, var.tags)
}

data "aws_availability_zones" "available" {
  state = "available"
}

# ---------------------------------------------------------------------------
# VPC
# ---------------------------------------------------------------------------

module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.0"

  name = local.name
  cidr = var.vpc_cidr

  azs             = slice(data.aws_availability_zones.available.names, 0, var.availability_zone_count)
  private_subnets = [for i in range(var.availability_zone_count) : cidrsubnet(var.vpc_cidr, 4, i)]
  public_subnets  = [for i in range(var.availability_zone_count) : cidrsubnet(var.vpc_cidr, 4, i + var.availability_zone_count)]

  enable_nat_gateway   = true
  single_nat_gateway   = var.environment != "prod"
  enable_dns_hostnames = true

  # Required tags for the EKS/ALB controllers to discover these subnets.
  public_subnet_tags = {
    "kubernetes.io/role/elb"              = "1"
    "kubernetes.io/cluster/${local.name}" = "shared"
  }
  private_subnet_tags = {
    "kubernetes.io/role/internal-elb"     = "1"
    "kubernetes.io/cluster/${local.name}" = "shared"
  }

  tags = local.common_tags
}

# ---------------------------------------------------------------------------
# EKS
# ---------------------------------------------------------------------------

module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name    = local.name
  cluster_version = var.eks_cluster_version

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  cluster_endpoint_public_access = true

  cluster_enabled_log_types = var.enable_control_plane_logging ? [
    "api", "audit", "authenticator", "controllerManager", "scheduler"
  ] : []

  eks_managed_node_groups = {
    default = {
      instance_types = var.eks_node_instance_types
      min_size       = var.eks_node_min_size
      max_size       = var.eks_node_max_size
      desired_size   = var.eks_node_desired_size
    }
  }

  # Lets rca-api's ServiceAccount assume an IAM role (IRSA) for Bedrock/CloudWatch/etc
  # without static credentials - see the aws_iam_role below.
  enable_irsa = true

  tags = local.common_tags
}

# ---------------------------------------------------------------------------
# ECR - one repository per deployable image
# ---------------------------------------------------------------------------

resource "aws_ecr_repository" "images" {
  for_each = toset(var.ecr_repository_names)

  name                 = "${local.name}/${each.value}"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = local.common_tags
}

resource "aws_ecr_lifecycle_policy" "images" {
  for_each   = aws_ecr_repository.images
  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Expire untagged images after 14 days"
      selection = {
        tagStatus   = "untagged"
        countType   = "sinceImagePushed"
        countUnit   = "days"
        countNumber = 14
      }
      action = { type = "expire" }
    }]
  })
}

# ---------------------------------------------------------------------------
# IAM role for rca-api's Kubernetes ServiceAccount (IRSA) - e.g. Bedrock, CloudWatch
# ---------------------------------------------------------------------------

data "aws_iam_policy_document" "rca_api_assume_role" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    effect  = "Allow"

    principals {
      type        = "Federated"
      identifiers = [module.eks.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${replace(module.eks.oidc_provider, "https://", "")}:sub"
      values   = ["system:serviceaccount:rca-platform:rca-platform"]
    }
  }
}

resource "aws_iam_role" "rca_api" {
  name               = "${local.name}-rca-api"
  assume_role_policy = data.aws_iam_policy_document.rca_api_assume_role.json
  tags               = local.common_tags
}

data "aws_iam_policy_document" "rca_api_permissions" {
  # AWS Bedrock, if AI_PROVIDER=BEDROCK.
  statement {
    sid       = "BedrockInvoke"
    effect    = "Allow"
    actions   = ["bedrock:InvokeModel", "bedrock:Converse"]
    resources = ["*"]
  }

  # CloudWatch, for the future Kubernetes/CloudWatch tools.
  statement {
    sid       = "CloudWatchRead"
    effect    = "Allow"
    actions   = ["cloudwatch:GetMetricData", "cloudwatch:ListMetrics", "logs:GetLogEvents", "logs:FilterLogEvents"]
    resources = ["*"]
  }

  statement {
    sid       = "SecretsManagerRead"
    effect    = "Allow"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.postgres_credentials.arn]
  }
}

resource "aws_iam_role_policy" "rca_api" {
  name   = "${local.name}-rca-api-permissions"
  role   = aws_iam_role.rca_api.id
  policy = data.aws_iam_policy_document.rca_api_permissions.json
}

# ---------------------------------------------------------------------------
# Secrets Manager - populate the actual secret value out-of-band (console/CI), never
# via a value committed to this repo.
# ---------------------------------------------------------------------------

resource "aws_secretsmanager_secret" "postgres_credentials" {
  name = "${local.name}/postgres-credentials"
  tags = local.common_tags
}
