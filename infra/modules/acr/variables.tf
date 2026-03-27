variable "acr_name" {
  description = "Globally unique name for the Azure Container Registry"
  type        = string
}

variable "location" {
  description = "Azure region"
  type        = string
}

variable "common_tags" {
  description = "Common tags for all resources"
  type        = map(string)
}
