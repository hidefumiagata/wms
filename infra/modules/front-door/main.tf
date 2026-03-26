resource "azurerm_cdn_frontdoor_profile" "main" {
  name                = "fd-wms-prd"
  resource_group_name = var.resource_group_name
  sku_name            = "Standard_AzureFrontDoor"

  tags = var.common_tags
}

resource "azurerm_cdn_frontdoor_endpoint" "main" {
  name                     = "wms-prd"
  cdn_frontdoor_profile_id = azurerm_cdn_frontdoor_profile.main.id
}

# --- API Origin Group (Active-Passive) ---

resource "azurerm_cdn_frontdoor_origin_group" "api" {
  name                     = "api-origin-group"
  cdn_frontdoor_profile_id = azurerm_cdn_frontdoor_profile.main.id

  health_probe {
    path                = "/actuator/health"
    protocol            = "Https"
    interval_in_seconds = 30
    request_type        = "GET"
  }

  load_balancing {
    sample_size                 = 4
    successful_samples_required = 3
  }
}

resource "azurerm_cdn_frontdoor_origin" "api_east" {
  name                           = "api-east"
  cdn_frontdoor_origin_group_id  = azurerm_cdn_frontdoor_origin_group.api.id
  host_name                      = var.backend_fqdn_east
  http_port                      = 80
  https_port                     = 443
  priority                       = 1
  weight                         = 1000
  enabled                        = true
  certificate_name_check_enabled = true
}

resource "azurerm_cdn_frontdoor_origin" "api_west" {
  name                           = "api-west"
  cdn_frontdoor_origin_group_id  = azurerm_cdn_frontdoor_origin_group.api.id
  host_name                      = var.backend_fqdn_west
  http_port                      = 80
  https_port                     = 443
  priority                       = 2
  weight                         = 1000
  enabled                        = true
  certificate_name_check_enabled = true
}

# API Route
resource "azurerm_cdn_frontdoor_route" "api" {
  name                          = "api-route"
  cdn_frontdoor_endpoint_id     = azurerm_cdn_frontdoor_endpoint.main.id
  cdn_frontdoor_origin_group_id = azurerm_cdn_frontdoor_origin_group.api.id
  patterns_to_match             = ["/api/*"]
  supported_protocols           = ["Https"]
  forwarding_protocol           = "HttpsOnly"
  cdn_frontdoor_origin_ids      = [azurerm_cdn_frontdoor_origin.api_east.id, azurerm_cdn_frontdoor_origin.api_west.id]
  link_to_default_domain        = true
}

# --- Static Website Origin Group ---

resource "azurerm_cdn_frontdoor_origin_group" "static" {
  name                     = "static-origin-group"
  cdn_frontdoor_profile_id = azurerm_cdn_frontdoor_profile.main.id

  load_balancing {
    sample_size                 = 4
    successful_samples_required = 3
  }
}

resource "azurerm_cdn_frontdoor_origin" "static_east" {
  name                           = "static-east"
  cdn_frontdoor_origin_group_id  = azurerm_cdn_frontdoor_origin_group.static.id
  host_name                      = var.static_website_hostname
  http_port                      = 80
  https_port                     = 443
  priority                       = 1
  weight                         = 1000
  enabled                        = true
  certificate_name_check_enabled = true
}

# Static Website Route
resource "azurerm_cdn_frontdoor_route" "static" {
  name                          = "static-route"
  cdn_frontdoor_endpoint_id     = azurerm_cdn_frontdoor_endpoint.main.id
  cdn_frontdoor_origin_group_id = azurerm_cdn_frontdoor_origin_group.static.id
  patterns_to_match             = ["/*"]
  supported_protocols           = ["Https"]
  forwarding_protocol           = "HttpsOnly"
  cdn_frontdoor_origin_ids      = [azurerm_cdn_frontdoor_origin.static_east.id]
  link_to_default_domain        = true
}

# --- WAF Policy ---

resource "azurerm_cdn_frontdoor_firewall_policy" "main" {
  name                = "wafwmsprd"
  resource_group_name = var.resource_group_name
  sku_name            = azurerm_cdn_frontdoor_profile.main.sku_name
  mode                = "Prevention"

  managed_rule {
    type    = "Microsoft_DefaultRuleSet"
    version = "2.1"
    action  = "Block"
  }
}

resource "azurerm_cdn_frontdoor_security_policy" "main" {
  name                     = "secpol-wms-prd"
  cdn_frontdoor_profile_id = azurerm_cdn_frontdoor_profile.main.id

  security_policies {
    firewall {
      cdn_frontdoor_firewall_policy_id = azurerm_cdn_frontdoor_firewall_policy.main.id
      association {
        domain {
          cdn_frontdoor_domain_id = azurerm_cdn_frontdoor_endpoint.main.id
        }
        patterns_to_match = ["/*"]
      }
    }
  }
}
