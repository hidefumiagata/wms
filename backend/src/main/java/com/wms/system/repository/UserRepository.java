package com.wms.system.repository;

import com.wms.system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserCode(String userCode);

    Optional<User> findByEmail(String email);

    Optional<User> findByUserCodeOrEmail(String userCode, String email);

    boolean existsByUserCode(String userCode);
}
