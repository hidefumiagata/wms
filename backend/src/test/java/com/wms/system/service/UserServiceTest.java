package com.wms.system.service;

import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.DuplicateResourceException;
import com.wms.shared.exception.OptimisticLockConflictException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.system.entity.User;
import com.wms.system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Nested
    @DisplayName("search")
    class Search {
        @Test
        @DisplayName("keywordフィルタで検索結果を返す")
        void search_withKeyword_returnsPagedResult() {
            User u = createUser(1L, "USR001", "山田太郎");
            Page<User> page = new PageImpl<>(List.of(u));
            Pageable pageable = PageRequest.of(0, 20);
            when(userRepository.search("USR001", null, null, pageable)).thenReturn(page);

            Page<User> result = userService.search("USR001", null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getUserCode()).isEqualTo("USR001");
        }

        @Test
        @DisplayName("roleフィルタで検索結果を返す")
        void search_withRole_returnsPagedResult() {
            User u = createUser(1L, "USR001", "山田太郎");
            u.setRole("SYSTEM_ADMIN");
            Page<User> page = new PageImpl<>(List.of(u));
            Pageable pageable = PageRequest.of(0, 20);
            when(userRepository.search(null, "SYSTEM_ADMIN", null, pageable)).thenReturn(page);

            Page<User> result = userService.search(null, "SYSTEM_ADMIN", null, pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("statusフィルタで検索結果を返す")
        void search_withStatus_returnsPagedResult() {
            User u = createUser(1L, "USR001", "山田太郎");
            Page<User> page = new PageImpl<>(List.of(u));
            Pageable pageable = PageRequest.of(0, 20);
            when(userRepository.search(null, null, "ACTIVE", pageable)).thenReturn(page);

            Page<User> result = userService.search(null, null, "ACTIVE", pageable);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {
        @Test
        @DisplayName("存在するIDでユーザーを返す")
        void findById_exists_returnsUser() {
            User u = createUser(1L, "USR001", "山田太郎");
            when(userRepository.findById(1L)).thenReturn(Optional.of(u));

            User result = userService.findById(1L);

            assertThat(result.getUserCode()).isEqualTo("USR001");
        }

        @Test
        @DisplayName("存在しないIDでResourceNotFoundExceptionをスロー")
        void findById_notExists_throwsException() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.findById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("ユーザー");
        }
    }

    @Nested
    @DisplayName("create")
    class Create {
        @Test
        @DisplayName("新規ユーザーを登録できる")
        void create_success() {
            User u = createUser(null, "USR002", "鈴木一郎");
            when(passwordEncoder.encode("Password@123")).thenReturn("$2a$12$hashed");
            when(userRepository.existsByUserCode("USR002")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User saved = inv.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            User result = userService.create(u, "Password@123");

            assertThat(result.getUserCode()).isEqualTo("USR002");
            assertThat(result.getPasswordChangeRequired()).isTrue();
            // パスワードがハッシュ化されていることを確認
            assertThat(result.getPasswordHash()).startsWith("$2a$12$");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("重複コードでDuplicateResourceExceptionをスロー")
        void create_duplicateCode_throwsException() {
            User u = createUser(null, "USR001", "山田太郎");
            u.setPasswordHash("plainPassword");
            when(userRepository.existsByUserCode("USR001")).thenReturn(true);

            assertThatThrownBy(() -> userService.create(u, "Password@123"))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("USR001");
        }

        @Test
        @DisplayName("TOCTOU競合時にDataIntegrityViolationExceptionをDuplicateResourceExceptionに変換")
        void create_tocTouRace_throwsDuplicateResourceException() {
            User u = createUser(null, "USR002", "鈴木一郎");
            u.setPasswordHash("plainPassword");
            when(userRepository.existsByUserCode("USR002")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("unique constraint"));

            assertThatThrownBy(() -> userService.create(u, "Password@123"))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("USR002");
        }
    }

    @Nested
    @DisplayName("update")
    class Update {
        @Test
        @DisplayName("ユーザー情報を更新できる")
        void update_success() {
            User existing = createUser(1L, "USR001", "山田太郎");
            existing.setRole("SYSTEM_ADMIN");
            when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserService.UpdateUserCommand cmd = new UserService.UpdateUserCommand(
                    1L, "山田次郎", "jiro@example.com", "SYSTEM_ADMIN", true, 0, 99L);

            User result = userService.update(cmd);

            assertThat(result.getFullName()).isEqualTo("山田次郎");
            assertThat(result.getEmail()).isEqualTo("jiro@example.com");
        }

        @Test
        @DisplayName("自己ロール変更禁止でBusinessRuleViolationExceptionをスロー")
        void update_selfRoleChange_throwsException() {
            User existing = createUser(1L, "USR001", "山田太郎");
            existing.setRole("SYSTEM_ADMIN");
            when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

            UserService.UpdateUserCommand cmd = new UserService.UpdateUserCommand(
                    1L, "山田太郎", "taro@example.com", "WAREHOUSE_MANAGER", true, 0, 1L);

            assertThatThrownBy(() -> userService.update(cmd))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("ロール");
        }

        @Test
        @DisplayName("自己無効化禁止でBusinessRuleViolationExceptionをスロー")
        void update_selfDeactivate_throwsException() {
            User existing = createUser(1L, "USR001", "山田太郎");
            existing.setRole("SYSTEM_ADMIN");
            when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

            UserService.UpdateUserCommand cmd = new UserService.UpdateUserCommand(
                    1L, "山田太郎", "taro@example.com", "SYSTEM_ADMIN", false, 0, 1L);

            assertThatThrownBy(() -> userService.update(cmd))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("無効化");
        }

        @Test
        @DisplayName("バージョン不一致でOptimisticLockConflictExceptionをスロー")
        void update_versionMismatch_throwsException() {
            User existing = createUser(1L, "USR001", "山田太郎");
            existing.setRole("SYSTEM_ADMIN");
            when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

            UserService.UpdateUserCommand cmd = new UserService.UpdateUserCommand(
                    1L, "山田次郎", "jiro@example.com", "SYSTEM_ADMIN", true, 99, 99L);

            assertThatThrownBy(() -> userService.update(cmd))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }

        @Test
        @DisplayName("楽観的ロック競合(JPA)でOptimisticLockConflictExceptionをスロー")
        void update_optimisticLockConflict_throwsException() {
            User existing = createUser(1L, "USR001", "山田太郎");
            existing.setRole("SYSTEM_ADMIN");
            when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(userRepository.save(any(User.class)))
                    .thenThrow(new ObjectOptimisticLockingFailureException(User.class.getName(), 1L));

            UserService.UpdateUserCommand cmd = new UserService.UpdateUserCommand(
                    1L, "山田次郎", "jiro@example.com", "SYSTEM_ADMIN", true, 0, 99L);

            assertThatThrownBy(() -> userService.update(cmd))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }

        @Test
        @DisplayName("存在しないIDでResourceNotFoundExceptionをスロー")
        void update_notFound_throwsException() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            UserService.UpdateUserCommand cmd = new UserService.UpdateUserCommand(
                    999L, "名前", "a@b.com", "SYSTEM_ADMIN", true, 0, 99L);

            assertThatThrownBy(() -> userService.update(cmd))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("toggleActive")
    class ToggleActive {
        @Test
        @DisplayName("ユーザーを無効化できる")
        void toggleActive_deactivate_success() {
            User existing = createUser(1L, "USR001", "山田太郎");
            when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.toggleActive(1L, false, 0, 99L);

            assertThat(result.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("自己無効化禁止でBusinessRuleViolationExceptionをスロー")
        void toggleActive_selfDeactivate_throwsException() {
            User existing = createUser(1L, "USR001", "山田太郎");
            when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> userService.toggleActive(1L, false, 0, 1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("無効化");
        }

        @Test
        @DisplayName("既に同じ状態の場合はUPDATEなし（冪等性）")
        void toggleActive_alreadySameState_noUpdate() {
            User existing = createUser(1L, "USR001", "山田太郎");
            when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

            User result = userService.toggleActive(1L, true, 0, 99L);

            assertThat(result.getIsActive()).isTrue();
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("バージョン不一致でOptimisticLockConflictExceptionをスロー")
        void toggleActive_versionMismatch_throwsException() {
            User existing = createUser(1L, "USR001", "山田太郎");
            when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> userService.toggleActive(1L, false, 99, 99L))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }

        @Test
        @DisplayName("楽観的ロック競合(JPA)でOptimisticLockConflictExceptionをスロー")
        void toggleActive_optimisticLockConflict_throwsException() {
            User existing = createUser(1L, "USR001", "山田太郎");
            when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(userRepository.save(any(User.class)))
                    .thenThrow(new ObjectOptimisticLockingFailureException(User.class.getName(), 1L));

            assertThatThrownBy(() -> userService.toggleActive(1L, false, 0, 99L))
                    .isInstanceOf(OptimisticLockConflictException.class);
        }
    }

    @Nested
    @DisplayName("unlock")
    class Unlock {
        @Test
        @DisplayName("ロック済みユーザーのロックを解除できる")
        void unlock_lockedUser_success() {
            User user = createUser(1L, "USR001", "山田太郎");
            user.setLocked(true);
            user.setFailedLoginCount(5);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.unlock(1L);

            assertThat(user.getLocked()).isFalse();
            assertThat(user.getFailedLoginCount()).isZero();
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("非ロックユーザーでも正常に処理される（冪等性）")
        void unlock_notLockedUser_success() {
            User user = createUser(1L, "USR001", "山田太郎");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.unlock(1L);

            assertThat(user.getLocked()).isFalse();
            verify(userRepository).save(user);
        }
    }

    @Nested
    @DisplayName("existsByCode")
    class ExistsByCode {
        @Test
        @DisplayName("存在するコードでtrueを返す")
        void existsByCode_exists_returnsTrue() {
            when(userRepository.existsByUserCode("USR001")).thenReturn(true);

            assertThat(userService.existsByCode("USR001")).isTrue();
        }

        @Test
        @DisplayName("存在しないコードでfalseを返す")
        void existsByCode_notExists_returnsFalse() {
            when(userRepository.existsByUserCode("XXXX")).thenReturn(false);

            assertThat(userService.existsByCode("XXXX")).isFalse();
        }
    }

    @Nested
    @DisplayName("getUserFullName")
    class GetUserFullName {
        @Test
        @DisplayName("存在するユーザーIDでフルネームを返す")
        void getUserFullName_exists_returnsFullName() {
            User u = createUser(10L, "USR010", "山田 太郎");
            when(userRepository.findById(10L)).thenReturn(Optional.of(u));

            assertThat(userService.getUserFullName(10L)).isEqualTo("山田 太郎");
        }

        @Test
        @DisplayName("存在しないユーザーIDでnullを返す")
        void getUserFullName_notExists_returnsNull() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThat(userService.getUserFullName(999L)).isNull();
        }

        @Test
        @DisplayName("nullのユーザーIDでnullを返す")
        void getUserFullName_null_returnsNull() {
            assertThat(userService.getUserFullName(null)).isNull();
            verify(userRepository, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("getUserFullNameMap")
    class GetUserFullNameMap {
        @Test
        @DisplayName("複数ユーザーIDでフルネームのマップを返す")
        void getUserFullNameMap_returnsMap() {
            User u1 = createUser(1L, "USR001", "山田太郎");
            User u2 = createUser(2L, "USR002", "鈴木一郎");
            when(userRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(u1, u2));

            Map<Long, String> result = userService.getUserFullNameMap(List.of(1L, 2L));

            assertThat(result).hasSize(2);
            assertThat(result.get(1L)).isEqualTo("山田太郎");
            assertThat(result.get(2L)).isEqualTo("鈴木一郎");
        }

        @Test
        @DisplayName("空のコレクションで空マップを返す")
        void getUserFullNameMap_empty_returnsEmptyMap() {
            assertThat(userService.getUserFullNameMap(List.of())).isEmpty();
            verify(userRepository, never()).findAllById(any());
        }

        @Test
        @DisplayName("nullで空マップを返す")
        void getUserFullNameMap_null_returnsEmptyMap() {
            assertThat(userService.getUserFullNameMap(null)).isEmpty();
            verify(userRepository, never()).findAllById(any());
        }
    }

    // --- Helper ---

    private User createUser(Long id, String code, String fullName) {
        User u = User.builder()
                .userCode(code)
                .fullName(fullName)
                .email(code.toLowerCase() + "@example.com")
                .passwordHash("$2a$12$dummyhash")
                .role("SYSTEM_ADMIN")
                .build();
        if (id != null) {
            u.setId(id);
        }
        return u;
    }
}
