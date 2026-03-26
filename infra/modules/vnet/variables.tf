variable "environment" {
  description = "Environment name (dev / prd-east / prd-west)"
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

variable "vnet_cidr" {
  description = "VNet CIDR block"
  type        = string
}

variable "snet_ca_cidr" {
  description = "Container Apps subnet CIDR (/23 minimum)"
  type        = string
}

variable "snet_pg_cidr" {
  description = "PostgreSQL subnet CIDR"
  type        = string
  default     = ""
}

variable "create_pg_subnet" {
  description = "Whether to create the PostgreSQL subnet (false for prd-west)"
  type        = bool
  default     = true
}

variable "enable_front_door" {
  description = "Whether to add Front Door NSG rules"
  type        = bool
  default     = false
}

variable "common_tags" {
  description = "Common tags for all resources"
  type        = map(string)
}
