package com.wms.master.controller;

import com.wms.generated.model.CreateProductRequest;
import com.wms.generated.model.ExistsResponse;
import com.wms.generated.model.ListProducts200Response;
import com.wms.generated.model.ProductDetail;
import com.wms.generated.model.ProductPageResponse;
import com.wms.generated.model.StorageCondition;
import com.wms.generated.model.ToggleActiveRequest;
import com.wms.generated.model.UpdateProductRequest;
import com.wms.master.entity.Product;
import com.wms.master.service.ProductService;
import com.wms.shared.exception.RateLimitExceededException;
import com.wms.shared.security.RateLimiterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * 商品マスタ CRUD コントローラー。
 * OpenAPI生成の MasterProductApi は商品専用インターフェースだが、
 * メソッドシグネチャの完全整合確認後に implements へのリファクタリングを行う設計とし、
 * 現フェーズでは個別コントローラーとして定義する。
 */
@RestController
@RequestMapping("/api/v1/master/products")
@RequiredArgsConstructor
@Validated
public class ProductController {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "productCode", "productName", "storageCondition", "isActive",
            "shipmentStopFlag", "createdAt", "updatedAt");
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductService productService;
    private final RateLimiterService rateLimiterService;

    /**
     * 商品一覧取得。all=true の場合はプルダウン用の全件リスト、
     * それ以外はページング形式で返却する。
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ListProducts200Response> listProducts(
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) StorageCondition storageCondition,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) Boolean shipmentStopFlag,
            @RequestParam(required = false) Boolean all,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) Integer size,
            @RequestParam(defaultValue = "productCode,asc") String sort) {

        String storageConditionValue = storageCondition != null ? storageCondition.getValue() : null;

        if (Boolean.TRUE.equals(all)) {
            // TODO: #75 パターン — プルダウン用途では ProductSimple への切り替えを検討（PII削減）
            List<ProductDetail> detailList = productService.findAllSimple(isActive).stream()
                    .map(this::toDetail)
                    .toList();
            ProductPageResponse response = new ProductPageResponse()
                    .content(detailList)
                    .page(0)
                    .size(detailList.size())
                    .totalElements((long) detailList.size())
                    .totalPages(detailList.isEmpty() ? 0 : 1);
            return ResponseEntity.ok(response);
        }

        int cappedSize = Math.min(size, MAX_PAGE_SIZE);
        Sort sortObj = parseSort(sort);
        Page<Product> resultPage = productService.search(
                productCode, productName, storageConditionValue, isActive, shipmentStopFlag,
                PageRequest.of(page, cappedSize, sortObj));
        return ResponseEntity.ok(toPageResponse(resultPage));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @PostMapping
    public ResponseEntity<ProductDetail> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        Product product = new Product();
        product.setProductCode(request.getProductCode());
        product.setProductName(request.getProductName());
        product.setProductNameKana(request.getProductNameKana());
        product.setCaseQuantity(request.getCaseQuantity());
        product.setBallQuantity(request.getBallQuantity());
        product.setBarcode(request.getBarcode());
        product.setStorageCondition(request.getStorageCondition().getValue());
        product.setIsHazardous(request.getIsHazardous());
        product.setLotManageFlag(request.getLotManageFlag());
        product.setExpiryManageFlag(request.getExpiryManageFlag());
        product.setShipmentStopFlag(request.getShipmentStopFlag());
        if (Boolean.FALSE.equals(request.getIsActive())) {
            product.deactivate();
        }

        Product created = productService.create(product);
        URI location = URI.create("/api/v1/master/products/" + created.getId());
        return ResponseEntity.created(location).body(toDetail(created));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<ProductDetail> getProduct(@PathVariable Long id) {
        Product product = productService.findById(id);
        return ResponseEntity.ok(toDetail(product));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @PutMapping("/{id}")
    public ResponseEntity<ProductDetail> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {
        Product updated = productService.update(new com.wms.master.service.UpdateProductCommand(
                id,
                request.getProductName(),
                request.getProductNameKana(),
                request.getCaseQuantity(),
                request.getBallQuantity(),
                request.getBarcode(),
                request.getStorageCondition().getValue(),
                request.getIsHazardous(),
                request.getLotManageFlag(),
                request.getExpiryManageFlag(),
                request.getShipmentStopFlag(),
                request.getIsActive(),
                request.getVersion()));
        return ResponseEntity.ok(toDetail(updated));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ProductDetail> toggleProductActive(
            @PathVariable Long id,
            @Valid @RequestBody ToggleActiveRequest request) {
        Product updated = productService.toggleActive(
                id, request.getIsActive(), request.getVersion());
        return ResponseEntity.ok(toDetail(updated));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/exists")
    public ResponseEntity<ExistsResponse> checkProductCodeExists(
            @RequestParam String productCode) {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && !rateLimiterService.tryConsumeCodeExists(auth.getName())) {
            throw new RateLimitExceededException();
        }
        boolean exists = productService.existsByCode(productCode);
        return ResponseEntity.ok(new ExistsResponse().exists(exists));
    }

    // --- Converters ---

    private ProductDetail toDetail(Product p) {
        return new ProductDetail()
                .id(p.getId())
                .productCode(p.getProductCode())
                .productName(p.getProductName())
                .productNameKana(p.getProductNameKana())
                .caseQuantity(p.getCaseQuantity())
                .ballQuantity(p.getBallQuantity())
                .barcode(p.getBarcode())
                .storageCondition(StorageCondition.fromValue(p.getStorageCondition()))
                .isHazardous(p.getIsHazardous())
                .lotManageFlag(p.getLotManageFlag())
                .expiryManageFlag(p.getExpiryManageFlag())
                .shipmentStopFlag(p.getShipmentStopFlag())
                .isActive(p.getIsActive())
                .version(p.getVersion())
                .createdAt(toLocalDateTime(p.getCreatedAt()))
                .updatedAt(toLocalDateTime(p.getUpdatedAt()));
    }

    private ProductPageResponse toPageResponse(Page<Product> page) {
        List<ProductDetail> items = page.getContent().stream()
                .map(this::toDetail)
                .toList();
        return new ProductPageResponse()
                .content(items)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages());
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime odt) {
        return odt != null ? odt.toLocalDateTime() : null;
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String property = ALLOWED_SORT_PROPERTIES.contains(parts[0])
                ? parts[0] : "productCode";
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
