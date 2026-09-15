terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Configure a real backend (S3 + DynamoDB lock table) before applying anywhere
  # shared. Left as local state here since a shared backend is environment-specific
  # and shouldn't be hardcoded into a POC deliverable.
  # backend "s3" {}
}

provider "aws" {
  region = var.aws_region
}
