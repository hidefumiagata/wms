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

variable "snet_ca_id" {
  description = "Container Apps subnet ID"
  type        = string
}

variable "log_analytics_workspace_id" {
  description = "Log Analytics Workspace ID"
  type        = string
}

variable "acr_login_server" {
  description = "ACR login server (e.g., acrwms.azurecr.io)"
  type        = string
}

variable "acr_id" {
  description = "ACR resource ID (for role assignment)"
  type        = string
}

variable "image_tag" {
  description = "Container image tag"
  type        = string
}

variable "min_replicas" {
  description = "Minimum replica count"
  type        = number
}

variable "max_replicas" {
  description = "Maximum replica count"
  type        = number
}

variable "spring_profile" {
  description = "Spring Boot active profile (dev / prd)"
  type        = string
}

variable "log_level" {
  description = "Application log level (DEBUG / INFO)"
  type        = string
}

variable "db_username" {
  description = "Database username"
  type        = string
  sensitive   = true
}

variable "db_connection_string" {
  description = "JDBC connection string"
  type        = string
  sensitive   = true
}

variable "db_password" {
  description = "Database password"
  type        = string
  sensitive   = true
}

variable "jwt_secret" {
  description = "JWT signing secret"
  type        = string
  sensitive   = true
}

variable "acs_connection_string" {
  description = "Azure Communication Services connection string"
  type        = string
  sensitive   = true
}

variable "acs_sender_address" {
  description = "Email sender address for ACS"
  type        = string
  default     = "DoNotReply@wms.example.com"
}

variable "storage_account_id" {
  description = "Storage Account resource ID (for role assignment)"
  type        = string
}

variable "storage_account_name" {
  description = "Storage Account name"
  type        = string
}

variable "app_insights_connection_string" {
  description = "Application Insights connection string"
  type        = string
}

variable "frontdoor_id" {
  description = "Front Door ID for X-Azure-FDID header validation (prd only)"
  type        = string
  default     = ""
}

variable "cors_allowed_origins" {
  description = "CORS allowed origins"
  type        = string
}

variable "common_tags" {
  description = "Common tags for all resources"
  type        = map(string)
}
