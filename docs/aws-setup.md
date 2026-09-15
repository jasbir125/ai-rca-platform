# AWS Setup

Terraform provisions the AWS side (`infrastructure/terraform`); Helm deploys the
application onto it (`infrastructure/helm/rca-platform`) — see `docs/eks-deployment.md`
for that half. Nothing here has been applied against a real AWS account during this
build (no credentials in this environment) — `terraform validate` passed and
`terraform plan`/`apply` are the next real verification step once you have an account.

## What Terraform provisions

- **VPC** (`terraform-aws-modules/vpc/aws`): public + private subnets across
  `availability_zone_count` AZs, one NAT gateway per AZ in prod (shared in
  dev/uat to control cost), tagged for EKS/ALB discovery.
- **EKS** (`terraform-aws-modules/eks/aws`): the control plane, a managed node group,
  IRSA enabled, control-plane logs to CloudWatch (`enable_control_plane_logging`).
- **ECR**: one repository per deployable image (`order-service`, `payment-service`,
  `rca-api`), image scanning on push, untagged images expire after 14 days.
- **IAM**: an IRSA role (`rca_api`) that the `rca-api` Kubernetes ServiceAccount can
  assume for Bedrock (`AI_PROVIDER=BEDROCK`), CloudWatch, and Secrets Manager —
  no static AWS credentials in any pod.
- **Secrets Manager**: a secret placeholder for Postgres credentials. Terraform creates
  the secret *resource*; populate the actual value out-of-band (console, CI variable,
  or `aws secretsmanager put-secret-value`) — never via a committed value.

## 1. Configure

```bash
cd infrastructure/terraform
cp terraform.tfvars.example terraform.tfvars
# edit terraform.tfvars: aws_region, environment, sizing - no account IDs or secrets
```

## 2. Provision

```bash
terraform init
terraform plan
terraform apply
```

## 3. Point kubectl at the new cluster

```bash
$(terraform output -raw configure_kubectl)
kubectl get nodes
```

## 4. Push images to ECR

```bash
aws ecr get-login-password --region <region> | docker login --username AWS --password-stdin <account>.dkr.ecr.<region>.amazonaws.com

for svc in order-service payment-service; do
  docker build -t <account>.dkr.ecr.<region>.amazonaws.com/rca-platform-dev/$svc:latest services/$svc
  docker push <account>.dkr.ecr.<region>.amazonaws.com/rca-platform-dev/$svc:latest
done
docker build -t <account>.dkr.ecr.<region>.amazonaws.com/rca-platform-dev/rca-api:latest -f rca-platform/rca-api/Dockerfile rca-platform
docker push <account>.dkr.ecr.<region>.amazonaws.com/rca-platform-dev/rca-api:latest
```

(The `.gitlab-ci.yml` `docker-build`/`docker-push` stages automate exactly this.)

## 5. Deploy the application

See `docs/eks-deployment.md` — in short, `helm upgrade --install` with
`image.registry` set to your ECR registry and `serviceAccount.annotations` set to the
`rca_api_iam_role_arn` Terraform output.

## AWS Bedrock instead of Ollama

Set `AI_PROVIDER=BEDROCK` in the Helm values (or ConfigMap directly). Note from
`plan.md`: `BedrockModelProvider` is not yet implemented in `agent-framework` — the
`AiModelProvider` interface and config switch are shaped for it, but building it now
(with no AWS credentials available to test against) would be unverified code. This is
explicitly next-up backlog work, not a gap that was overlooked.

## Cost note

The Terraform defaults (`m6i.large` × 2-6 nodes, one NAT gateway per AZ outside prod)
are reasonable for a real dev/UAT environment, not a "spin up and tear down in an
hour" sandbox — review `eks_node_*` and `availability_zone_count` variables before
applying if cost matters more than realism for your use case.
