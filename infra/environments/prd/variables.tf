variable "environment" {
  description = "Environment name"
  type        = string
}

variable "acr_name" {
  description = "Globally unique name for the Azure Container Registry"
  type        = string
}

variable "location_primary" {
  description = "Primary Azure region (Japan East)"
  type        = string
}

variable "location_secondary" {
  description = "Secondary Azure region (Japan West)"
  type        = string
}

variable "subscription_id" {
  description = "Azure subscription ID for prd environment"
  type        = string
}

# --- Container Apps ---

variable "min_replicas_east" {
  description = "Minimum replica count (East)"
  type        = number
}

variable "max_replicas_east" {
  description = "Maximum replica count (East)"
  type        = number
}

variable "min_replicas_west" {
  description = "Minimum replica count (West)"
  type        = number
}

variable "max_replicas_west" {
  description = "Maximum replica count (West)"
  type        = number
}

variable "image_tag" {
  description = "Container image tag (semver)"
  type        = string
  default     = "latest"
}

variable "log_level" {
  description = "Application log level"
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

# --- Network (East) ---

variable "vnet_east_cidr" {
  description = "East VNet CIDR block"
  type        = string
}

variable "snet_ca_east_cidr" {
  description = "East Container Apps subnet CIDR"
  type        = string
}

variable "snet_pg_east_cidr" {
  description = "East PostgreSQL subnet CIDR"
  type        = string
}

# --- Network (West) ---

variable "vnet_west_cidr" {
  description = "West VNet CIDR block"
  type        = string
}

variable "snet_ca_west_cidr" {
  description = "West Container Apps subnet CIDR"
  type        = string
}

# --- Front Door ---

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
