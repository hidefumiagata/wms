output "storage_account_id" {
  description = "Storage Account resource ID"
  value       = azurerm_storage_account.main.id
}

output "storage_account_name" {
  description = "Storage Account name"
  value       = azurerm_storage_account.main.name
}

output "static_website_url" {
  description = "Static website primary URL"
  value       = azurerm_storage_account.main.primary_web_endpoint
}

output "primary_web_host" {
  description = "Static website primary host (for Front Door origin)"
  value       = azurerm_storage_account.main.primary_web_host
}
