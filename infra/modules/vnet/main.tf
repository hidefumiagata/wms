resource "azurerm_virtual_network" "main" {
  name                = "vnet-wms-${var.environment}"
  location            = var.location
  resource_group_name = var.resource_group_name
  address_space       = [var.vnet_cidr]
  tags                = var.common_tags
}

resource "azurerm_subnet" "ca" {
  name                 = "snet-ca-${var.environment}"
  resource_group_name  = var.resource_group_name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [var.snet_ca_cidr]
  delegation {
    name = "container-apps"
    service_delegation {
      name    = "Microsoft.App/environments"
      actions = ["Microsoft.Network/virtualNetworks/subnets/join/action"]
    }
  }
}

resource "azurerm_subnet" "pg" {
  count                = var.create_pg_subnet ? 1 : 0
  name                 = "snet-pg-${var.environment}"
  resource_group_name  = var.resource_group_name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [var.snet_pg_cidr]
  delegation {
    name = "postgresql"
    service_delegation {
      name    = "Microsoft.DBforPostgreSQL/flexibleServers"
      actions = ["Microsoft.Network/virtualNetworks/subnets/join/action"]
    }
  }
}

# --- NSG ---

resource "azurerm_network_security_group" "ca" {
  name                = "nsg-ca-${var.environment}"
  location            = var.location
  resource_group_name = var.resource_group_name
  tags                = var.common_tags
}

resource "azurerm_network_security_group" "pg" {
  count               = var.create_pg_subnet ? 1 : 0
  name                = "nsg-pg-${var.environment}"
  location            = var.location
  resource_group_name = var.resource_group_name
  tags                = var.common_tags
}

# --- snet-ca NSG rules ---

# Front Door無効時: Internet全体からHTTPSを許可（dev環境）
resource "azurerm_network_security_rule" "ca_inbound_https" {
  count                       = var.enable_front_door ? 0 : 1
  name                        = "Allow-HTTPS-Inbound"
  priority                    = 100
  direction                   = "Inbound"
  access                      = "Allow"
  protocol                    = "Tcp"
  source_port_range           = "*"
  destination_port_range      = "443"
  source_address_prefix       = "Internet"
  destination_address_prefix  = "*"
  resource_group_name         = var.resource_group_name
  network_security_group_name = azurerm_network_security_group.ca.name
}

# Front Door有効時: Front Doorからのみ許可（prd環境）
resource "azurerm_network_security_rule" "ca_inbound_frontdoor" {
  count                       = var.enable_front_door ? 1 : 0
  name                        = "Allow-FrontDoor-Inbound"
  priority                    = 100
  direction                   = "Inbound"
  access                      = "Allow"
  protocol                    = "Tcp"
  source_port_range           = "*"
  destination_port_range      = "443"
  source_address_prefix       = "AzureFrontDoor.Backend"
  destination_address_prefix  = "*"
  resource_group_name         = var.resource_group_name
  network_security_group_name = azurerm_network_security_group.ca.name
}

resource "azurerm_network_security_rule" "ca_inbound_lb" {
  name                        = "Allow-AzureLB-Inbound"
  priority                    = 200
  direction                   = "Inbound"
  access                      = "Allow"
  protocol                    = "*"
  source_port_range           = "*"
  destination_port_range      = "*"
  source_address_prefix       = "AzureLoadBalancer"
  destination_address_prefix  = "*"
  resource_group_name         = var.resource_group_name
  network_security_group_name = azurerm_network_security_group.ca.name
}

resource "azurerm_network_security_rule" "ca_inbound_deny_all" {
  name                        = "Deny-All-Inbound"
  priority                    = 4096
  direction                   = "Inbound"
  access                      = "Deny"
  protocol                    = "*"
  source_port_range           = "*"
  destination_port_range      = "*"
  source_address_prefix       = "*"
  destination_address_prefix  = "*"
  resource_group_name         = var.resource_group_name
  network_security_group_name = azurerm_network_security_group.ca.name
}

# Local PG subnet outbound (dev, prd-east)
resource "azurerm_network_security_rule" "ca_outbound_pg" {
  count                       = var.create_pg_subnet ? 1 : 0
  name                        = "Allow-PostgreSQL-Outbound"
  priority                    = 100
  direction                   = "Outbound"
  access                      = "Allow"
  protocol                    = "Tcp"
  source_port_range           = "*"
  destination_port_range      = "5432"
  source_address_prefix       = "*"
  destination_address_prefix  = azurerm_subnet.pg[0].address_prefixes[0]
  resource_group_name         = var.resource_group_name
  network_security_group_name = azurerm_network_security_group.ca.name
}

# Cross-region PG outbound (prd-west → prd-east PG via VNet Peering)
resource "azurerm_network_security_rule" "ca_outbound_pg_remote" {
  count                       = var.remote_pg_cidr != "" ? 1 : 0
  name                        = "Allow-PostgreSQL-Remote-Outbound"
  priority                    = 105
  direction                   = "Outbound"
  access                      = "Allow"
  protocol                    = "Tcp"
  source_port_range           = "*"
  destination_port_range      = "5432"
  source_address_prefix       = "*"
  destination_address_prefix  = var.remote_pg_cidr
  resource_group_name         = var.resource_group_name
  network_security_group_name = azurerm_network_security_group.ca.name
}

resource "azurerm_network_security_rule" "ca_outbound_https" {
  name                        = "Allow-HTTPS-Outbound"
  priority                    = 110
  direction                   = "Outbound"
  access                      = "Allow"
  protocol                    = "Tcp"
  source_port_range           = "*"
  destination_port_range      = "443"
  source_address_prefix       = "*"
  destination_address_prefix  = "Internet"
  resource_group_name         = var.resource_group_name
  network_security_group_name = azurerm_network_security_group.ca.name
}

resource "azurerm_network_security_rule" "ca_outbound_deny_all" {
  name                        = "Deny-All-Outbound"
  priority                    = 4096
  direction                   = "Outbound"
  access                      = "Deny"
  protocol                    = "*"
  source_port_range           = "*"
  destination_port_range      = "*"
  source_address_prefix       = "*"
  destination_address_prefix  = "*"
  resource_group_name         = var.resource_group_name
  network_security_group_name = azurerm_network_security_group.ca.name
}

# --- snet-pg NSG rules ---

resource "azurerm_network_security_rule" "pg_inbound_ca" {
  count                       = var.create_pg_subnet ? 1 : 0
  name                        = "Allow-CA-PostgreSQL-Inbound"
  priority                    = 100
  direction                   = "Inbound"
  access                      = "Allow"
  protocol                    = "Tcp"
  source_port_range           = "*"
  destination_port_range      = "5432"
  source_address_prefix       = azurerm_subnet.ca.address_prefixes[0]
  destination_address_prefix  = "*"
  resource_group_name         = var.resource_group_name
  network_security_group_name = azurerm_network_security_group.pg[0].name
}

resource "azurerm_network_security_rule" "pg_inbound_deny_all" {
  count                       = var.create_pg_subnet ? 1 : 0
  name                        = "Deny-All-Inbound"
  priority                    = 4096
  direction                   = "Inbound"
  access                      = "Deny"
  protocol                    = "*"
  source_port_range           = "*"
  destination_port_range      = "*"
  source_address_prefix       = "*"
  destination_address_prefix  = "*"
  resource_group_name         = var.resource_group_name
  network_security_group_name = azurerm_network_security_group.pg[0].name
}

resource "azurerm_network_security_rule" "pg_outbound_deny_all" {
  count                       = var.create_pg_subnet ? 1 : 0
  name                        = "Deny-All-Outbound"
  priority                    = 4096
  direction                   = "Outbound"
  access                      = "Deny"
  protocol                    = "*"
  source_port_range           = "*"
  destination_port_range      = "*"
  source_address_prefix       = "*"
  destination_address_prefix  = "*"
  resource_group_name         = var.resource_group_name
  network_security_group_name = azurerm_network_security_group.pg[0].name
}

# --- NSG associations ---

resource "azurerm_subnet_network_security_group_association" "ca" {
  subnet_id                 = azurerm_subnet.ca.id
  network_security_group_id = azurerm_network_security_group.ca.id
}

resource "azurerm_subnet_network_security_group_association" "pg" {
  count                     = var.create_pg_subnet ? 1 : 0
  subnet_id                 = azurerm_subnet.pg[0].id
  network_security_group_id = azurerm_network_security_group.pg[0].id
}

# --- Private DNS Zone ---

resource "azurerm_private_dns_zone" "postgres" {
  count               = var.create_pg_subnet ? 1 : 0
  name                = "privatelink.postgres.database.azure.com"
  resource_group_name = var.resource_group_name
  tags                = var.common_tags
}

resource "azurerm_private_dns_zone_virtual_network_link" "postgres" {
  count                 = var.create_pg_subnet ? 1 : 0
  name                  = "postgres-vnet-link"
  resource_group_name   = var.resource_group_name
  private_dns_zone_name = azurerm_private_dns_zone.postgres[0].name
  virtual_network_id    = azurerm_virtual_network.main.id
  registration_enabled  = false
}
