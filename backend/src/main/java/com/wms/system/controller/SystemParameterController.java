package com.wms.system.controller;

import com.wms.generated.api.SystemParameterApi;
import com.wms.generated.model.SystemParameterCategory;
import com.wms.generated.model.SystemParameterDetail;
import com.wms.generated.model.SystemParameterValueType;
import com.wms.generated.model.UpdateSystemParameterRequest;
import com.wms.system.entity.SystemParameter;
import com.wms.system.service.SystemParameterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * システムパラメータ管理 API コントローラー。
 * OpenAPI生成の SystemParameterApi を実装する。
 */
@RestController
@RequiredArgsConstructor
public class SystemParameterController implements SystemParameterApi {

    private final SystemParameterService systemParameterService;

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Override
    public ResponseEntity<List<SystemParameterDetail>> getSystemParameters() {
        List<SystemParameterDetail> details = systemParameterService.findAll().stream()
                .map(this::toDetail)
                .toList();
        return ResponseEntity.ok(details);
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Override
    public ResponseEntity<SystemParameterDetail> updateSystemParameter(
            String paramKey,
            UpdateSystemParameterRequest updateSystemParameterRequest) {
        SystemParameter updated = systemParameterService.updateValue(
                paramKey, updateSystemParameterRequest.getParamValue());
        return ResponseEntity.ok(toDetail(updated));
    }

    // --- Converter ---

    private SystemParameterDetail toDetail(SystemParameter p) {
        return new SystemParameterDetail()
                .paramKey(p.getParamKey())
                .paramValue(p.getParamValue())
                .defaultValue(p.getDefaultValue())
                .displayName(p.getDisplayName())
                .category(SystemParameterCategory.fromValue(p.getCategory()))
                .valueType(SystemParameterValueType.fromValue(p.getValueType()))
                .description(p.getDescription())
                .updatedAt(p.getUpdatedAt())
                .updatedBy(p.getUpdatedBy());
    }
}
