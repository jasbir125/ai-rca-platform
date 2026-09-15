variable "aws_region" {
  description = "AWS region to deploy into."
  type        = string
}

variable "project_name" {
  description = "Short name used to prefix/tag all resources."
  type        = string
  default     = "rca-platform"
}

variable "environment" {
  description = "Deployment environment: dev, uat, or prod."
  type        = string
  validation {
    condition     = contains(["dev", "uat", "prod"], var.environment)
    error_message = "environment must be one of: dev, uat, prod."
  }
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.42.0.0/16"
}

variable "availability_zone_count" {
  description = "Number of AZs to spread subnets across."
  type        = number
  default     = 3
}

variable "eks_cluster_version" {
  description = "Kubernetes version for the EKS control plane."
  type        = string
  default     = "1.30"
}

variable "eks_node_instance_types" {
  description = "Instance types for the EKS managed node group."
  type        = list(string)
  default     = ["m6i.large"]
}

variable "eks_node_desired_size" {
  type    = number
  default = 3
}

variable "eks_node_min_size" {
  type    = number
  default = 2
}

variable "eks_node_max_size" {
  type    = number
  default = 6
}

variable "ecr_repository_names" {
  description = "One ECR repository per deployable image."
  type        = list(string)
  default     = ["order-service", "payment-service", "rca-api"]
}

variable "enable_control_plane_logging" {
  description = "Whether to ship EKS control plane logs to CloudWatch."
  type        = bool
  default     = true
}

variable "tags" {
  description = "Extra tags applied to every resource this stack creates."
  type        = map(string)
  default     = {}
}
