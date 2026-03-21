package com.wms.master.controller;

import com.wms.generated.model.CreatePartnerRequest;
import com.wms.generated.model.ExistsResponse;
import com.wms.generated.model.ListPartners200Response;
import com.wms.generated.model.PartnerDetail;
import com.wms.generated.model.PartnerPageResponse;
import com.wms.generated.model.PartnerType;
import com.wms.generated.model.ToggleActiveRequest;
import com.wms.generated.model.UpdatePartnerRequest;
import com.wms.master.entity.Partner;
import com.wms.master.service.PartnerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * 取引先マスタ CRUD コントローラー。
 * OpenAPI生成の MasterPartnerApi は取引先専用インターフェースだが、
 * メソッドシグネチャの完全整合確認後に implements へのリファクタリングを行う設計とし、
 * 現フェーズでは個別コントローラーとして定義する。
 */
@RestController
@RequestMapping("/api/v1/master/partners")
@RequiredArgsConstructor
@Validated
public class PartnerController {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "partnerCode", "partnerName", "partnerType", "isActive", "createdAt", "updatedAt");
    private static final int MAX_PAGE_SIZE = 100;

    private final PartnerService partnerService;

    /**
     * 取引先一覧取得。all=true の場合はプルダウン用の全件リスト、
     * それ以外はページング形式で返却する。
     */
    @GetMapping
    public ResponseEntity<ListPartners200Response> listPartners(
            @RequestParam(required = false) String partnerCode,
            @RequestParam(required = false) String partnerName,
            @RequestParam(required = false) PartnerType partnerType,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) Boolean all,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) Integer size,
            @RequestParam(defaultValue = "partnerCode,asc") String sort) {

        String partnerTypeValue = partnerType != null ? partnerType.getValue() : null;

        if (Boolean.TRUE.equals(all)) {
            // TODO: #75 プルダウン用途では PartnerSimple（code/name/type/isActive のみ）への切り替えを検討（PII削減）
            List<PartnerDetail> detailList = partnerService.findAllSimple(isActive).stream()
                    .map(this::toDetail)
                    .toList();
            PartnerPageResponse response = new PartnerPageResponse()
                    .content(detailList)
                    .page(0)
                    .size(detailList.size())
                    .totalElements((long) detailList.size())
                    .totalPages(detailList.isEmpty() ? 0 : 1);
            return ResponseEntity.ok(response);
        }

        int cappedSize = Math.min(size, MAX_PAGE_SIZE);
        Sort sortObj = parseSort(sort);
        Page<Partner> resultPage = partnerService.search(
                partnerCode, partnerName, partnerTypeValue, isActive,
                PageRequest.of(page, cappedSize, sortObj));
        return ResponseEntity.ok(toPageResponse(resultPage));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @PostMapping
    public ResponseEntity<PartnerDetail> createPartner(
            @Valid @RequestBody CreatePartnerRequest request) {
        Partner partner = new Partner();
        partner.setPartnerCode(request.getPartnerCode());
        partner.setPartnerName(request.getPartnerName());
        partner.setPartnerNameKana(request.getPartnerNameKana());
        partner.setPartnerType(request.getPartnerType().getValue());
        partner.setAddress(request.getAddress());
        partner.setPhone(request.getPhone());
        partner.setContactPerson(request.getContactPerson());
        partner.setEmail(request.getEmail());

        Partner created = partnerService.create(partner);
        URI location = URI.create("/api/v1/master/partners/" + created.getId());
        return ResponseEntity.created(location).body(toDetail(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PartnerDetail> getPartner(@PathVariable Long id) {
        Partner partner = partnerService.findById(id);
        return ResponseEntity.ok(toDetail(partner));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @PutMapping("/{id}")
    public ResponseEntity<PartnerDetail> updatePartner(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePartnerRequest request) {
        Partner updated = partnerService.update(
                id,
                request.getPartnerName(),
                request.getPartnerNameKana(),
                request.getPartnerType().getValue(),
                request.getAddress(),
                request.getPhone(),
                request.getContactPerson(),
                request.getEmail(),
                request.getVersion());
        return ResponseEntity.ok(toDetail(updated));
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<PartnerDetail> togglePartnerActive(
            @PathVariable Long id,
            @Valid @RequestBody ToggleActiveRequest request) {
        Partner updated = partnerService.toggleActive(
                id, request.getIsActive(), request.getVersion());
        return ResponseEntity.ok(toDetail(updated));
    }

    // TODO: #74 列挙攻撃対策として RateLimiterService の適用を検討
    @GetMapping("/exists")
    public ResponseEntity<ExistsResponse> checkPartnerCodeExists(
            @RequestParam String partnerCode) {
        boolean exists = partnerService.existsByCode(partnerCode);
        return ResponseEntity.ok(new ExistsResponse().exists(exists));
    }

    // --- Converters ---

    private PartnerDetail toDetail(Partner p) {
        return new PartnerDetail()
                .id(p.getId())
                .partnerCode(p.getPartnerCode())
                .partnerName(p.getPartnerName())
                .partnerNameKana(p.getPartnerNameKana())
                .partnerType(PartnerType.fromValue(p.getPartnerType()))
                .address(p.getAddress())
                .phone(p.getPhone())
                .contactPerson(p.getContactPerson())
                .email(p.getEmail())
                .isActive(p.getIsActive())
                .version(p.getVersion())
                .createdAt(toLocalDateTime(p.getCreatedAt()))
                .updatedAt(toLocalDateTime(p.getUpdatedAt()));
    }

    private PartnerPageResponse toPageResponse(Page<Partner> page) {
        List<PartnerDetail> items = page.getContent().stream()
                .map(this::toDetail)
                .toList();
        return new PartnerPageResponse()
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
                ? parts[0] : "partnerCode";
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
