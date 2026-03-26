output "storage_account_name" {
  description = "Terraform state storage account name"
  value       = azurerm_storage_account.terraform.name
}

output "container_name" {
  description = "Terraform state blob container name"
  value       = azurerm_storage_container.tfstate.name
}

output "resource_group_name" {
  description = "Terraform state resource group name"
  value       = azurerm_resource_group.terraform.name
}
