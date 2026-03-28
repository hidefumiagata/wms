package com.wms.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WmsUserDetails")
class WmsUserDetailsTest {

    private WmsUserDetails create(Long userId, String username, String warehouseCode) {
        return new WmsUserDetails(userId, username, "password",
                warehouseCode, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Nested
    @DisplayName("equals")
    class EqualsTests {

        @Test
        @DisplayName("同一オブジェクトはtrue")
        void sameObject_returnsTrue() {
            WmsUserDetails details = create(1L, "user1", "WH01");
            assertThat(details.equals(details)).isTrue();
        }

        @Test
        @DisplayName("異なる型はfalse")
        void differentType_returnsFalse() {
            WmsUserDetails details = create(1L, "user1", "WH01");
            assertThat(details.equals("not a WmsUserDetails")).isFalse();
        }

        @Test
        @DisplayName("nullはfalse")
        void null_returnsFalse() {
            WmsUserDetails details = create(1L, "user1", "WH01");
            assertThat(details.equals(null)).isFalse();
        }

        @Test
        @DisplayName("同一フィールドはtrue")
        void sameFields_returnsTrue() {
            WmsUserDetails a = create(1L, "user1", "WH01");
            WmsUserDetails b = create(1L, "user1", "WH01");
            assertThat(a.equals(b)).isTrue();
        }

        @Test
        @DisplayName("super.equals()がfalse（異なるusername）")
        void differentUsername_returnsFalse() {
            WmsUserDetails a = create(1L, "user1", "WH01");
            WmsUserDetails b = create(1L, "user2", "WH01");
            assertThat(a.equals(b)).isFalse();
        }

        @Test
        @DisplayName("異なるuserIdはfalse")
        void differentUserId_returnsFalse() {
            WmsUserDetails a = create(1L, "user1", "WH01");
            WmsUserDetails b = create(2L, "user1", "WH01");
            assertThat(a.equals(b)).isFalse();
        }

        @Test
        @DisplayName("異なるwarehouseCodeはfalse")
        void differentWarehouseCode_returnsFalse() {
            WmsUserDetails a = create(1L, "user1", "WH01");
            WmsUserDetails b = create(1L, "user1", "WH02");
            assertThat(a.equals(b)).isFalse();
        }
    }

    @Nested
    @DisplayName("hashCode")
    class HashCodeTests {

        @Test
        @DisplayName("同一フィールドは同一ハッシュ")
        void sameFields_sameHash() {
            WmsUserDetails a = create(1L, "user1", "WH01");
            WmsUserDetails b = create(1L, "user1", "WH01");
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }
    }
}
