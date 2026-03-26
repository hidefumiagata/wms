output "frontend_url" {
  description = "Frontend static website URL"
  value       = module.storage.static_website_url
}

output "backend_url_east" {
  description = "East Backend Container App FQDN"
  value       = module.container_apps_east.fqdn
}

output "backend_url_west" {
  description = "West Backend Container App FQDN"
  value       = module.container_apps_west.fqdn
}

output "acr_login_server" {
  description = "ACR login server"
  value       = module.acr.acr_login_server
}

output "db_fqdn" {
  description = "PostgreSQL server FQDN"
  value       = module.postgresql.server_fqdn
}

output "app_insights_connection_string" {
  description = "Application Insights connection string"
  value       = module.monitoring.app_insights_connection_string
  sensitive   = true
}

output "frontdoor_endpoint" {
  description = "Front Door endpoint hostname"
  value       = module.front_door.frontdoor_endpoint_hostname
}
