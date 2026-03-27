variable "environment" {
  description = "Environment name"
  type        = string
}

variable "acr_name" {
  description = "Globally unique name for the Azure Container Registry"
  type        = string
}

variable "location" {
  description = "Azure region"
  type        = string
}

variable "subscription_id" {
  description = "Azure subscription ID for this environment"
  type        = string
}

# --- Container Apps ---

variable "min_replicas" {
  description = "Minimum replica count"
  type        = number
}

variable "max_replicas" {
  description = "Maximum replica count"
  type        = number
}

variable "image_tag" {
  description = "Container image tag"
  type        = string
}

variable "log_level" {
  description = "Application log level (DEBUG / INFO)"
  type        = string
}

# --- PostgreSQL ---

variable "db_sku_name" {
  description = "PostgreSQL SKU name"
  type        = string
}

variable "db_storage_mb" {
  description = "PostgreSQL storage size in MB"
  type        = number
}

variable "db_geo_backup" {
  description = "Enable geo-redundant backup"
  type        = bool
}

variable "db_auto_grow" {
  description = "Enable storage auto-grow"
  type        = bool
}

variable "db_admin_password" {
  description = "PostgreSQL administrator password"
  type        = string
  sensitive   = true
}

# --- Storage ---

variable "storage_replication" {
  description = "Storage replication type (LRS / GRS)"
  type        = string
}

# --- Network ---

variable "vnet_cidr" {
  description = "VNet CIDR block"
  type        = string
}

variable "snet_ca_cidr" {
  description = "Container Apps subnet CIDR"
  type        = string
}

variable "snet_pg_cidr" {
  description = "PostgreSQL subnet CIDR"
  type        = string
}

variable "enable_front_door" {
  description = "Enable Front Door"
  type        = bool
}

# --- Secrets ---

variable "jwt_secret" {
  description = "JWT signing secret key (>= 32 characters)"
  type        = string
  sensitive   = true
}

# --- Monitoring ---

variable "alert_email" {
  description = "Email address for alert notifications"
  type        = string
  default     = "admin@wms.example.com"
}
