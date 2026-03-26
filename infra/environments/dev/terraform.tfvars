# environments/dev/terraform.tfvars
environment     = "dev"
location        = "japaneast"
subscription_id = "xxxxxxxx-dev-subscription-id"

# Container Apps
min_replicas = 0
max_replicas = 3
image_tag    = "sha-latest"
log_level    = "DEBUG"

# PostgreSQL
db_sku_name   = "B_Standard_B1ms"
db_storage_mb = 32768
db_geo_backup = false
db_auto_grow  = false

# Storage
storage_replication = "LRS"

# Network
vnet_cidr    = "10.0.0.0/16"
snet_ca_cidr = "10.0.0.0/23"
snet_pg_cidr = "10.0.2.0/24"

# Front Door
enable_front_door = false
