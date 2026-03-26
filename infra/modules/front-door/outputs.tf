output "frontdoor_id" {
  description = "Front Door profile resource ID"
  value       = azurerm_cdn_frontdoor_profile.main.id
}

output "frontdoor_resource_guid" {
  description = "Front Door resource GUID (for X-Azure-FDID header validation)"
  value       = azurerm_cdn_frontdoor_profile.main.resource_guid
}

output "frontdoor_endpoint_hostname" {
  description = "Front Door endpoint hostname"
  value       = azurerm_cdn_frontdoor_endpoint.main.host_name
}
