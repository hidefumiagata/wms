variable "resource_group_name" {
  description = "Global resource group name"
  type        = string
}

variable "backend_fqdn_east" {
  description = "East Container App FQDN"
  type        = string
}

variable "backend_fqdn_west" {
  description = "West Container App FQDN"
  type        = string
}

variable "static_website_hostname" {
  description = "Static website hostname (Blob Storage)"
  type        = string
}

variable "common_tags" {
  description = "Common tags for all resources"
  type        = map(string)
}
