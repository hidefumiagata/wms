package com.wms.interfacing.model;

import java.time.LocalDate;

/**
 * 伝票番号の採番インターフェース。テスト時にモック可能。
 */
@FunctionalInterface
public interface SlipNumberGenerator {
    String generate(LocalDate businessDate);
}
