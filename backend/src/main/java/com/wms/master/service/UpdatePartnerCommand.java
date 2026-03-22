package com.wms.master.service;

import com.wms.master.entity.PartnerType;

/**
 * 取引先更新用の内部コマンド DTO。
 * Controller → Service 間で使用し、引数の順序ミスを防止する。
 */
public record UpdatePartnerCommand(
        Long id,
        String partnerName,
        String partnerNameKana,
        PartnerType partnerType,
        String address,
        String phone,
        String contactPerson,
        String email,
        Integer version
) {}
