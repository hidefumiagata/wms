package com.wms.shared.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

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
}
