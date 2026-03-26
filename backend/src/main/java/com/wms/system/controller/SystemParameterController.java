package com.wms.system.controller;

import com.wms.generated.api.SystemParameterApi;
import com.wms.generated.model.SystemParameterCategory;
import com.wms.generated.model.SystemParameterDetail;
import com.wms.generated.model.SystemParameterValueType;
import com.wms.generated.model.UpdateSystemParameterRequest;
import com.wms.system.entity.SystemParameter;
import com.wms.system.service.SystemParameterService;
import com.wms.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * システムパラメータ管理 API コントローラー。
 * OpenAPI生成の SystemParameterApi を実装する。
 */
@RestController
@RequiredArgsConstructor
public class SystemParameterController implements SystemParameterApi {

    private final SystemParameterService systemParameterService;
    private final UserService userService;

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Override
    public ResponseEntity<List<SystemParameterDetail>> getSystemParameters() {
        List<SystemParameter> params = systemParameterService.findAll();

        // N+1回避: updatedBy のユーザーIDを一括取得
        Set<Long> userIds = params.stream()
                .map(SystemParameter::getUpdatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> userNameMap = userService.getUserFullNameMap(userIds);

        List<SystemParameterDetail> details = params.stream()
                .map(p -> toDetail(p, userNameMap))
                .toList();
        return ResponseEntity.ok(details);
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Override
    public ResponseEntity<SystemParameterDetail> updateSystemParameter(
            String paramKey,
            UpdateSystemParameterRequest updateSystemParameterRequest) {
        SystemParameter updated = systemParameterService.updateValue(
                paramKey, updateSystemParameterRequest.getParamValue(),
                updateSystemParameterRequest.getVersion());
        String updatedByName = userService.getUserFullName(updated.getUpdatedBy());
        return ResponseEntity.ok(toDetail(updated, updatedByName));
    }

    // --- Converter ---

    private SystemParameterDetail toDetail(SystemParameter p, Map<Long, String> userNameMap) {
        return toDetail(p, p.getUpdatedBy() != null ? userNameMap.get(p.getUpdatedBy()) : null);
    }

    private SystemParameterDetail toDetail(SystemParameter p, String updatedByName) {
        return new SystemParameterDetail()
                .paramKey(p.getParamKey())
                .paramValue(p.getParamValue())
                .defaultValue(p.getDefaultValue())
                .displayName(p.getDisplayName())
                .category(SystemParameterCategory.fromValue(p.getCategory()))
                .valueType(SystemParameterValueType.fromValue(p.getValueType()))
                .description(p.getDescription())
                .updatedAt(p.getUpdatedAt())
                .updatedBy(p.getUpdatedBy())
                .updatedByName(updatedByName)
                .version(p.getVersion());
    }
}
