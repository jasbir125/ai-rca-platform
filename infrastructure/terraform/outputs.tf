output "vpc_id" {
  value = module.vpc.vpc_id
}

output "eks_cluster_name" {
  value = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "eks_oidc_provider_arn" {
  value = module.eks.oidc_provider_arn
}

output "ecr_repository_urls" {
  value = { for name, repo in aws_ecr_repository.images : name => repo.repository_url }
}

output "rca_api_iam_role_arn" {
  description = "Pass this to the Helm chart's serviceAccount.annotations for IRSA."
  value       = aws_iam_role.rca_api.arn
}

output "postgres_secret_arn" {
  value = aws_secretsmanager_secret.postgres_credentials.arn
}

output "configure_kubectl" {
  value = "aws eks update-kubeconfig --region ${var.aws_region} --name ${module.eks.cluster_name}"
}
