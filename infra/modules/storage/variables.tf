variable "environment" {
  description = "Environment name (dev / prdeast)"
  type        = string
}

variable "location" {
  description = "Azure region"
  type        = string
}

variable "resource_group_name" {
  description = "Resource group name"
  type        = string
}

variable "storage_replication" {
  description = "Replication type (LRS / GRS)"
  type        = string
}

variable "cors_allowed_origins" {
  description = "CORS allowed origins for static website"
  type        = list(string)
}

variable "common_tags" {
  description = "Common tags for all resources"
  type        = map(string)
}
