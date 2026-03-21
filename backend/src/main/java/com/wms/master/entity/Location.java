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
@Table(name = "locations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Location extends MasterBaseEntity {

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private Long warehouseId;

    @Column(name = "area_id", nullable = false, updatable = false)
    private Long areaId;

    @Column(name = "location_code", nullable = false, updatable = false, length = 50)
    private String locationCode;

    @Column(name = "location_name", length = 200)
    private String locationName;

    @Column(name = "is_stocktaking_locked", nullable = false)
    private Boolean isStocktakingLocked = false;
}
