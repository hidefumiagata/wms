package com.wms.interfacing.model;

import com.wms.master.entity.Partner;
import com.wms.master.entity.Product;
import com.wms.master.entity.Warehouse;
import lombok.Getter;

import java.util.Map;

/**
 * マスタデータのキャッシュ。CSV内のユニークコードを一括検索して保持する。
 */
@Getter
public class MasterCache {
    private final Map<String, Partner> partnerMap;
    private final Map<String, Product> productMap;
    private final Warehouse warehouse;

    public MasterCache(Map<String, Partner> partnerMap, Map<String, Product> productMap,
                       Warehouse warehouse) {
        this.partnerMap = partnerMap;
        this.productMap = productMap;
        this.warehouse = warehouse;
    }

    public Partner getPartner(String code) {
        return partnerMap.get(code);
    }

    public Product getProduct(String code) {
        return productMap.get(code);
    }
}
