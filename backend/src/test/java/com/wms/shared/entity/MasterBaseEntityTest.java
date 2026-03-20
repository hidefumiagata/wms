package com.wms.shared.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MasterBaseEntityTest {

    /** テスト用の具象サブクラス */
    @Entity
    @Table(name = "test_entity")
    static class TestMasterEntity extends MasterBaseEntity {
    }

    @Test
    @DisplayName("初期状態: isActive=true, version=0")
    void initialState_isActiveTrue_versionZero() {
        TestMasterEntity entity = new TestMasterEntity();

        assertThat(entity.getIsActive()).isTrue();
        assertThat(entity.getVersion()).isEqualTo(0);
    }

    @Test
    @DisplayName("deactivate(): isActiveがfalseになる")
    void deactivate_setsIsActiveFalse() {
        TestMasterEntity entity = new TestMasterEntity();

        entity.deactivate();

        assertThat(entity.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("activate(): isActiveがtrueに戻る")
    void activate_setsIsActiveTrue() {
        TestMasterEntity entity = new TestMasterEntity();
        entity.deactivate();

        entity.activate();

        assertThat(entity.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("deactivate -> activate の往復で元に戻る")
    void deactivateAndActivate_roundTrip() {
        TestMasterEntity entity = new TestMasterEntity();

        entity.deactivate();
        assertThat(entity.getIsActive()).isFalse();

        entity.activate();
        assertThat(entity.getIsActive()).isTrue();
    }
}
