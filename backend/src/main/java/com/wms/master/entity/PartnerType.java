package com.wms.master.entity;

/**
 * 取引先種別。DB では {@code @Enumerated(EnumType.STRING)} で文字列として格納される。
 */
public enum PartnerType {
    SUPPLIER,
    CUSTOMER,
    BOTH
}
