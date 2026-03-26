variable "terraform_subscription_id" {
  description = "Azure subscription ID for wms-terraform (Terraform state storage)"
  type        = string
  sensitive   = true
}
