output "vnet_id" {
  description = "VNet resource ID"
  value       = azurerm_virtual_network.main.id
}

output "snet_ca_id" {
  description = "Container Apps subnet ID"
  value       = azurerm_subnet.ca.id
}

output "snet_pg_id" {
  description = "PostgreSQL subnet ID (empty if create_pg_subnet is false)"
  value       = var.create_pg_subnet ? azurerm_subnet.pg[0].id : ""
}

output "private_dns_zone_id" {
  description = "Private DNS zone ID for PostgreSQL (empty if create_pg_subnet is false)"
  value       = var.create_pg_subnet ? azurerm_private_dns_zone.postgres[0].id : ""
}

output "private_dns_zone_name" {
  description = "Private DNS zone name (empty if create_pg_subnet is false)"
  value       = var.create_pg_subnet ? azurerm_private_dns_zone.postgres[0].name : ""
}
