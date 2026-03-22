package com.wms.master.service;

/**
 * 商品更新用の内部コマンド DTO。
 * Controller → Service 間で使用し、引数の順序ミスを防止する。
 */
public record UpdateProductCommand(
        Long id,
        String productName,
        String productNameKana,
        Integer caseQuantity,
        Integer ballQuantity,
        String barcode,
        String storageCondition,
        Boolean isHazardous,
        Boolean lotManageFlag,
        Boolean expiryManageFlag,
        Boolean shipmentStopFlag,
        Boolean isActive,
        Integer version
) {}
