# environments/prd/terraform.tfvars
# subscription_id is set via environment variable: TF_VAR_subscription_id
environment        = "prd"
acr_name           = "acrwmsprdshowcase"
location_primary   = "japaneast"
location_secondary = "japanwest"

# Container Apps (East)
min_replicas_east = 1
max_replicas_east = 5

# Container Apps (West)
min_replicas_west = 0
max_replicas_west = 5

log_level = "INFO"

# PostgreSQL
db_sku_name   = "B_Standard_B1ms"
db_storage_mb = 32768
db_geo_backup = true
db_auto_grow  = true

# Storage
storage_replication = "GRS"

# Network (East)
vnet_east_cidr    = "10.1.0.0/16"
snet_ca_east_cidr = "10.1.0.0/23"
snet_pg_east_cidr = "10.1.2.0/24"

# Network (West)
vnet_west_cidr    = "10.2.0.0/16"
snet_ca_west_cidr = "10.2.0.0/23"

# Front Door
enable_front_door = true
