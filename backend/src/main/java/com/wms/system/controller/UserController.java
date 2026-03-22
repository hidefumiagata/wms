package com.wms.system.controller;

import com.wms.generated.api.MasterUserApi;
import com.wms.generated.model.CreateUserRequest;
import com.wms.generated.model.ExistsResponse;
import com.wms.generated.model.ToggleActiveRequest;
import com.wms.generated.model.UpdateUserRequest;
import com.wms.generated.model.UserDetail;
import com.wms.generated.model.UserPageResponse;
import com.wms.generated.model.UserRole;
import com.wms.generated.model.UserStatus;
import com.wms.shared.exception.RateLimitExceededException;
import com.wms.shared.security.RateLimiterService;
import com.wms.shared.security.WmsUserDetails;
import com.wms.system.entity.User;
import com.wms.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * ユーザーマスタ CRUD コントローラー。
 * OpenAPI生成の MasterUserApi を実装する。
 */
@RestController
@RequiredArgsConstructor
public class UserController implements MasterUserApi {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "userCode", "fullName", "email", "role", "createdAt", "updatedAt");

    private final UserService userService;
    private final RateLimiterService rateLimiterService;

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Override
    public ResponseEntity<UserPageResponse> listUsers(
            String keyword,
            UserRole role,
            UserStatus status,
            Boolean all,
            Integer page,
            Integer size,
            String sort) {

        String roleStr = role != null ? role.getValue() : null;
        String statusStr = status != null ? status.getValue() : null;

        if (Boolean.TRUE.equals(all)) {
            Page<User> allUsers = userService.search(keyword, roleStr, statusStr,
                    PageRequest.of(0, Integer.MAX_VALUE, parseSort(sort)));
            List<UserDetail> items = allUsers.getContent().stream()
                    .map(this::toDetail)
                    .toList();
            UserPageResponse response = new UserPageResponse()
                    .content(items)
                    .page(0)
                    .size(items.size())
                    .totalElements((long) items.size())
                    .totalPages(1);
            return ResponseEntity.ok(response);
        }

        Sort sortObj = parseSort(sort);
        Page<User> resultPage = userService.search(keyword, roleStr, statusStr,
                PageRequest.of(page, size, sortObj));
        return ResponseEntity.ok(toPageResponse(resultPage));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Override
    public ResponseEntity<UserDetail> createUser(CreateUserRequest createUserRequest) {
        User user = new User();
        user.setUserCode(createUserRequest.getUserCode());
        user.setFullName(createUserRequest.getFullName());
        user.setEmail(createUserRequest.getEmail());
        user.setRole(createUserRequest.getRole().getValue());
        user.setPasswordHash(createUserRequest.getInitialPassword());

        User created = userService.create(user);
        URI location = URI.create("/api/v1/master/users/" + created.getId());
        return ResponseEntity.created(location).body(toDetail(created));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Override
    public ResponseEntity<UserDetail> getUser(Long id) {
        User user = userService.findById(id);
        return ResponseEntity.ok(toDetail(user));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Override
    public ResponseEntity<UserDetail> updateUser(Long id, UpdateUserRequest updateUserRequest) {
        UserService.UpdateUserCommand cmd = new UserService.UpdateUserCommand(
                id,
                updateUserRequest.getFullName(),
                updateUserRequest.getEmail(),
                updateUserRequest.getRole().getValue(),
                updateUserRequest.getIsActive(),
                updateUserRequest.getVersion(),
                getCurrentUserId());
        User updated = userService.update(cmd);
        return ResponseEntity.ok(toDetail(updated));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Override
    public ResponseEntity<UserDetail> toggleUserActive(Long id,
            ToggleActiveRequest toggleActiveRequest) {
        User updated = userService.toggleActive(
                id,
                toggleActiveRequest.getIsActive(),
                toggleActiveRequest.getVersion(),
                getCurrentUserId());
        return ResponseEntity.ok(toDetail(updated));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Override
    public ResponseEntity<Void> unlockUser(Long id) {
        userService.unlock(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Override
    public ResponseEntity<ExistsResponse> checkUserCodeExists(String code) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && !rateLimiterService.tryConsumeCodeExists(auth.getName())) {
            throw new RateLimitExceededException();
        }
        boolean exists = userService.existsByCode(code);
        return ResponseEntity.ok(new ExistsResponse().exists(exists));
    }

    // --- Helper ---

    private Long getCurrentUserId() {
        WmsUserDetails userDetails = (WmsUserDetails) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return userDetails.getUserId();
    }

    // --- Converters ---

    private UserDetail toDetail(User u) {
        return new UserDetail()
                .id(u.getId())
                .userCode(u.getUserCode())
                .fullName(u.getFullName())
                .email(u.getEmail())
                .role(UserRole.fromValue(u.getRole()))
                .isActive(u.getIsActive())
                .locked(u.getLocked())
                .lockedAt(u.getLockedAt())
                .passwordChangeRequired(u.getPasswordChangeRequired())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .version(u.getVersion());
    }

    private UserPageResponse toPageResponse(Page<User> page) {
        List<UserDetail> items = page.getContent().stream()
                .map(this::toDetail)
                .toList();
        return new UserPageResponse()
                .content(items)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages());
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String property = ALLOWED_SORT_PROPERTIES.contains(parts[0])
                ? parts[0] : "userCode";
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
