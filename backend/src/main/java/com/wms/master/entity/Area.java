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
@Table(name = "areas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Area extends MasterBaseEntity {

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private Long warehouseId;

    @Column(name = "building_id", nullable = false, updatable = false)
    private Long buildingId;

    @Column(name = "area_code", nullable = false, updatable = false, length = 20)
    private String areaCode;

    @Column(name = "area_name", nullable = false, length = 200)
    private String areaName;

    // TODO: #70 パターン — @Enumerated(EnumType.STRING) + Java Enum への移行を検討
    @Column(name = "storage_condition", nullable = false, length = 20)
    private String storageCondition;

    // TODO: #70 パターン — @Enumerated(EnumType.STRING) + Java Enum への移行を検討
    @Column(name = "area_type", nullable = false, updatable = false, length = 20)
    private String areaType;
}
