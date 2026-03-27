package com.wms.shared.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.Objects;

@Getter
public class WmsUserDetails extends User {

    private final Long userId;
    private final String warehouseCode;

    public WmsUserDetails(Long userId,
                          String username,
                          String password,
                          String warehouseCode,
                          Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.userId = userId;
        this.warehouseCode = warehouseCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WmsUserDetails that)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        return Objects.equals(userId, that.userId)
                && Objects.equals(warehouseCode, that.warehouseCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), userId, warehouseCode);
    }
}
