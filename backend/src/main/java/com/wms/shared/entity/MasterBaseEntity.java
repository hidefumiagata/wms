package com.wms.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;

@MappedSuperclass
@Getter
public abstract class MasterBaseEntity extends BaseEntity {

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    /**
     * 楽観的ロック用にversionをセットする。
     * フロントエンドから受け取ったversionを設定し、JPA @Versionによる競合検出を行う。
     */
    public void setVersion(Integer version) {
        this.version = version;
    }
}
