package com.wms.system.controller;

import com.wms.generated.api.SystemApi;
import com.wms.generated.model.BusinessDateResponse;
import com.wms.generated.model.SessionConfigResponse;
import com.wms.system.service.SystemParameterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/**
 * システム共通 API コントローラー。
 */
@RestController
@RequiredArgsConstructor
public class SystemController implements SystemApi {

    private static final String SESSION_TIMEOUT_KEY = "SESSION_TIMEOUT_MINUTES";
    private static final int WARNING_OFFSET_MINUTES = 5;

    private final SystemParameterService systemParameterService;

    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<SessionConfigResponse> getSessionConfig() {
        int timeoutMinutes = systemParameterService.getIntValue(SESSION_TIMEOUT_KEY);
        int warningMinutes = Math.max(timeoutMinutes - WARNING_OFFSET_MINUTES, 0);

        return ResponseEntity.ok(new SessionConfigResponse()
                .timeoutMinutes(timeoutMinutes)
                .warningMinutes(warningMinutes));
    }

    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<BusinessDateResponse> getBusinessDate() {
        // TODO: business_dates テーブル実装後に実データを返却
        return ResponseEntity.ok(new BusinessDateResponse()
                .businessDate(java.time.LocalDate.now()));
    }
}
