package com.wms.master.entity;

import com.wms.shared.entity.MasterBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Product extends MasterBaseEntity {

    @Column(name = "product_code", nullable = false, unique = true, length = 50)
    private String productCode;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "product_name_kana", length = 200)
    private String productNameKana;

    @Column(name = "case_quantity", nullable = false)
    private Integer caseQuantity;

    @Column(name = "ball_quantity", nullable = false)
    private Integer ballQuantity;

    @Column(name = "barcode", length = 100)
    private String barcode;

    // TODO: #70 パターン — @Enumerated(EnumType.STRING) + Java Enum への移行を検討
    //       現フェーズは JPQL の等値比較との相性を考慮して String のまま維持
    @Column(name = "storage_condition", nullable = false, length = 20)
    private String storageCondition;

    @Column(name = "is_hazardous", nullable = false)
    private Boolean isHazardous = false;

    @Column(name = "lot_manage_flag", nullable = false)
    private Boolean lotManageFlag = false;

    @Column(name = "expiry_manage_flag", nullable = false)
    private Boolean expiryManageFlag = false;

    @Column(name = "shipment_stop_flag", nullable = false)
    private Boolean shipmentStopFlag = false;
}
