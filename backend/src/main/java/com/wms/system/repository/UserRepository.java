package com.wms.system.repository;

import com.wms.system.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserCode(String userCode);

    Optional<User> findByEmail(String email);

    Optional<User> findByUserCodeOrEmail(String userCode, String email);

    boolean existsByUserCode(String userCode);

    @Query("SELECT u FROM User u WHERE "
        + "(:keyword IS NULL OR (LOWER(u.userCode) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
        + "    OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\')) "
        + "AND (:role IS NULL OR u.role = :role) "
        + "AND (:status IS NULL "
        + "    OR (:status = 'ACTIVE' AND u.isActive = true AND u.locked = false) "
        + "    OR (:status = 'INACTIVE' AND u.isActive = false) "
        + "    OR (:status = 'LOCKED' AND u.locked = true))")
    Page<User> search(@Param("keyword") String keyword, @Param("role") String role,
                       @Param("status") String status, Pageable pageable);
}
