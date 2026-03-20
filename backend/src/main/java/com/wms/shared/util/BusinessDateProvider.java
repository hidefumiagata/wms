package com.wms.shared.util;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 営業日を提供するユーティリティ。
 * ShowCase規模では休日カレンダーは管理しない。
 * 現在日をそのまま営業日として返す。
 */
@Component
public class BusinessDateProvider {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    public LocalDate today() {
        return LocalDate.now(JST);
    }
}
