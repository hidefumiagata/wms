package com.wms.system.service;

import static com.wms.shared.util.LikeEscapeUtil.escape;

import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.DuplicateResourceException;
import com.wms.shared.exception.OptimisticLockConflictException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.system.entity.User;
import com.wms.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public record UpdateUserCommand(Long id, String fullName, String email, String role,
                                     Boolean isActive, Integer version, Long currentUserId) {}

    public Page<User> search(String keyword, String role, String status, Pageable pageable) {
        return userRepository.search(escape(keyword), role, status, pageable);
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(
                        "USER_NOT_FOUND", "ユーザー", id));
    }

    @Transactional
    public User create(User user, String rawPassword) {
        if (userRepository.existsByUserCode(user.getUserCode())) {
            throw new DuplicateResourceException("DUPLICATE_CODE",
                    "ユーザーコードが既に存在します: " + user.getUserCode());
        }
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setPasswordChangeRequired(true);
        try {
            User created = userRepository.save(user);
            log.info("User created: code={}", created.getUserCode());
            return created;
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException("DUPLICATE_CODE",
                    "ユーザーコードが既に存在します: " + user.getUserCode());
        }
    }

    @Transactional
    public User update(UpdateUserCommand cmd) {
        User user = findById(cmd.id());
        if (!user.getVersion().equals(cmd.version())) {
            throw new OptimisticLockConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + cmd.id() + ")");
        }
        // 自己ロール変更禁止
        if (cmd.currentUserId().equals(cmd.id())
                && !user.getRole().equals(cmd.role())) {
            throw new BusinessRuleViolationException("CANNOT_CHANGE_SELF_ROLE",
                    "自分自身のロールは変更できません");
        }
        // 自己無効化禁止
        if (cmd.currentUserId().equals(cmd.id())
                && Boolean.FALSE.equals(cmd.isActive())) {
            throw new BusinessRuleViolationException("CANNOT_DEACTIVATE_SELF",
                    "自分自身を無効化することはできません");
        }
        user.setFullName(cmd.fullName());
        user.setEmail(cmd.email());
        user.setRole(cmd.role());
        user.setIsActive(cmd.isActive());
        user.setVersion(cmd.version());
        try {
            User saved = userRepository.save(user);
            log.info("User updated: id={}, fullName={}", cmd.id(), cmd.fullName());
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + cmd.id() + ")");
        }
    }

    @Transactional
    public User toggleActive(Long id, boolean isActive, Integer version, Long currentUserId) {
        User user = findById(id);
        // 自己無効化禁止
        if (currentUserId.equals(id) && !isActive) {
            throw new BusinessRuleViolationException("CANNOT_DEACTIVATE_SELF",
                    "自分自身を無効化することはできません");
        }
        if (!user.getVersion().equals(version)) {
            throw new OptimisticLockConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }
        // 冪等性: 既に同じ状態なら更新しない
        if (user.getIsActive().equals(isActive)) {
            log.info("User toggleActive no-op: id={}, isActive={}", id, isActive);
            return user;
        }
        user.setIsActive(isActive);
        user.setVersion(version);
        try {
            User saved = userRepository.save(user);
            log.info("User toggled: id={}, isActive={}", id, isActive);
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (id=" + id + ")");
        }
    }

    @Transactional
    public void unlock(Long id) {
        User user = findById(id);
        user.unlock();
        userRepository.save(user);
        log.info("User unlocked: id={}", id);
    }

    public boolean existsByCode(String userCode) {
        return userRepository.existsByUserCode(userCode);
    }

    /**
     * ユーザーIDからフルネームを取得する。
     * ユーザーが存在しない場合やIDがnullの場合はnullを返す。
     */
    public String getUserFullName(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(User::getFullName)
                .orElse(null);
    }
}
