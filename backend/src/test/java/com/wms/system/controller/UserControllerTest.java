package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.generated.model.CreateUserRequest;
import com.wms.generated.model.ToggleActiveRequest;
import com.wms.generated.model.UpdateUserRequest;
import com.wms.generated.model.UserRole;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.DuplicateResourceException;
import com.wms.shared.exception.OptimisticLockConflictException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.security.JwtAuthenticationFilter;
import com.wms.shared.security.JwtTokenProvider;
import com.wms.shared.security.RateLimiterService;
import com.wms.shared.security.WmsUserDetails;
import com.wms.system.entity.User;
import com.wms.system.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("UserController")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    private static final String BASE_URL = "/api/v1/master/users";

    @BeforeEach
    void setUpSecurityContext() {
        WmsUserDetails userDetails = new WmsUserDetails(
                99L, "admin", "password", null,
                List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")));
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ========== listUsers ==========

    @Nested
    @DisplayName("GET /api/v1/master/users")
    class ListTests {

        @Test
        @DisplayName("ページング形式で一覧を返す")
        void list_paged_returnsPageResponse() throws Exception {
            User u = createUser(1L, "USR001", "山田太郎");
            var page = new PageImpl<>(List.of(u), PageRequest.of(0, 20), 1);
            when(userService.search(any(), any(), any(), any())).thenReturn(page);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].userCode").value("USR001"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("descソートで一覧を返す")
        void list_paged_descSort() throws Exception {
            User u = createUser(1L, "USR001", "山田太郎");
            var page = new PageImpl<>(List.of(u), PageRequest.of(0, 20), 1);
            when(userService.search(any(), any(), any(), any())).thenReturn(page);

            mockMvc.perform(get(BASE_URL).param("sort", "fullName,desc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("許可されていないソートプロパティはデフォルトにフォールバック")
        void list_paged_invalidSortProperty_fallbackToDefault() throws Exception {
            User u = createUser(1L, "USR001", "山田太郎");
            var page = new PageImpl<>(List.of(u), PageRequest.of(0, 20), 1);
            when(userService.search(any(), any(), any(), any())).thenReturn(page);

            mockMvc.perform(get(BASE_URL).param("sort", "malicious_column,asc"))
                    .andExpect(status().isOk());

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(userService).search(any(), any(), any(), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        }

        @Test
        @DisplayName("all=trueでUserPageResponse形式の全件リストを返す")
        void list_all_returnsWrappedList() throws Exception {
            User u = createUser(1L, "USR001", "山田太郎");
            var page = new PageImpl<>(List.of(u));
            when(userService.search(any(), any(), any(), any())).thenReturn(page);

            mockMvc.perform(get(BASE_URL).param("all", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].userCode").value("USR001"))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test
        @DisplayName("ページサイズが上限100を超える場合は400を返す")
        void list_paged_sizeExceeding100_returns400() throws Exception {
            mockMvc.perform(get(BASE_URL).param("size", "101"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("空ページを返す")
        void list_paged_emptyResult() throws Exception {
            var page = new PageImpl<User>(List.of(), PageRequest.of(0, 20), 0);
            when(userService.search(any(), any(), any(), any())).thenReturn(page);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    // ========== createUser ==========

    @Nested
    @DisplayName("POST /api/v1/master/users")
    class CreateTests {

        @Test
        @DisplayName("正常な登録リクエストで201を返す")
        void create_success_returns201() throws Exception {
            User created = createUser(1L, "USR002", "鈴木一郎");
            when(userService.create(any(User.class), any(String.class))).thenReturn(created);

            CreateUserRequest request = new CreateUserRequest()
                    .userCode("USR002")
                    .fullName("鈴木一郎")
                    .email("suzuki@example.com")
                    .role(UserRole.WAREHOUSE_STAFF)
                    .initialPassword("Password1!");

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v1/master/users/1"))
                    .andExpect(jsonPath("$.userCode").value("USR002"))
                    .andExpect(jsonPath("$.fullName").value("鈴木一郎"));
        }

        @Test
        @DisplayName("重複コードで409を返す")
        void create_duplicateCode_returns409() throws Exception {
            when(userService.create(any(User.class), any(String.class)))
                    .thenThrow(new DuplicateResourceException("DUPLICATE_CODE", "重複"));

            CreateUserRequest request = new CreateUserRequest()
                    .userCode("USR001")
                    .fullName("山田太郎")
                    .email("taro@example.com")
                    .role(UserRole.SYSTEM_ADMIN)
                    .initialPassword("Password1!");

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("必須項目未設定で400を返す")
        void create_missingRequired_returns400() throws Exception {
            CreateUserRequest request = new CreateUserRequest();

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ========== getUser ==========

    @Nested
    @DisplayName("GET /api/v1/master/users/{id}")
    class GetTests {

        @Test
        @DisplayName("存在するIDで200を返す")
        void get_exists_returns200() throws Exception {
            User u = createUser(1L, "USR001", "山田太郎");
            when(userService.findById(1L)).thenReturn(u);

            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userCode").value("USR001"))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void get_notFound_returns404() throws Exception {
            when(userService.findById(999L))
                    .thenThrow(ResourceNotFoundException.of("USER_NOT_FOUND", "ユーザー", 999L));

            mockMvc.perform(get(BASE_URL + "/999"))
                    .andExpect(status().isNotFound());
        }
    }

    // ========== updateUser ==========

    @Nested
    @DisplayName("PUT /api/v1/master/users/{id}")
    class UpdateTests {

        @Test
        @DisplayName("正常な更新で200を返す")
        void update_success_returns200() throws Exception {
            User updated = createUser(1L, "USR001", "山田次郎");
            when(userService.update(any(UserService.UpdateUserCommand.class))).thenReturn(updated);

            UpdateUserRequest request = new UpdateUserRequest()
                    .fullName("山田次郎")
                    .email("jiro@example.com")
                    .role(UserRole.SYSTEM_ADMIN)
                    .isActive(true)
                    .version(0);

            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fullName").value("山田次郎"));
        }

        @Test
        @DisplayName("楽観的ロック競合で409を返す")
        void update_conflict_returns409() throws Exception {
            when(userService.update(any(UserService.UpdateUserCommand.class)))
                    .thenThrow(new OptimisticLockConflictException("OPTIMISTIC_LOCK_CONFLICT", "競合"));

            UpdateUserRequest request = new UpdateUserRequest()
                    .fullName("名前")
                    .email("a@b.com")
                    .role(UserRole.SYSTEM_ADMIN)
                    .isActive(true)
                    .version(0);

            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("自己ロール変更で422を返す")
        void update_selfRoleChange_returns422() throws Exception {
            when(userService.update(any(UserService.UpdateUserCommand.class)))
                    .thenThrow(new BusinessRuleViolationException("CANNOT_CHANGE_SELF_ROLE", "自分自身のロールは変更できません"));

            UpdateUserRequest request = new UpdateUserRequest()
                    .fullName("名前")
                    .email("a@b.com")
                    .role(UserRole.WAREHOUSE_MANAGER)
                    .isActive(true)
                    .version(0);

            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void update_notFound_returns404() throws Exception {
            when(userService.update(any(UserService.UpdateUserCommand.class)))
                    .thenThrow(ResourceNotFoundException.of("USER_NOT_FOUND", "ユーザー", 999L));

            UpdateUserRequest request = new UpdateUserRequest()
                    .fullName("名前")
                    .email("a@b.com")
                    .role(UserRole.SYSTEM_ADMIN)
                    .isActive(true)
                    .version(0);

            mockMvc.perform(put(BASE_URL + "/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    // ========== toggleUserActive ==========

    @Nested
    @DisplayName("PATCH /api/v1/master/users/{id}/toggle-active")
    class ToggleTests {

        @Test
        @DisplayName("無効化で200を返す")
        void toggle_deactivate_returns200() throws Exception {
            User u = createUser(1L, "USR001", "山田太郎");
            u.setIsActive(false);
            when(userService.toggleActive(eq(1L), eq(false), eq(0), any())).thenReturn(u);

            ToggleActiveRequest request = new ToggleActiveRequest()
                    .isActive(false)
                    .version(0);

            mockMvc.perform(patch(BASE_URL + "/1/toggle-active")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isActive").value(false));
        }

        @Test
        @DisplayName("自己無効化で422を返す")
        void toggle_selfDeactivate_returns422() throws Exception {
            when(userService.toggleActive(eq(1L), eq(false), eq(0), any()))
                    .thenThrow(new BusinessRuleViolationException("CANNOT_DEACTIVATE_SELF", "自分自身を無効化することはできません"));

            ToggleActiveRequest request = new ToggleActiveRequest()
                    .isActive(false)
                    .version(0);

            mockMvc.perform(patch(BASE_URL + "/1/toggle-active")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("楽観的ロック競合で409を返す")
        void toggle_conflict_returns409() throws Exception {
            when(userService.toggleActive(eq(1L), eq(false), eq(0), any()))
                    .thenThrow(new OptimisticLockConflictException("OPTIMISTIC_LOCK_CONFLICT", "競合"));

            ToggleActiveRequest request = new ToggleActiveRequest()
                    .isActive(false)
                    .version(0);

            mockMvc.perform(patch(BASE_URL + "/1/toggle-active")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    // ========== unlockUser ==========

    @Nested
    @DisplayName("PATCH /api/v1/master/users/{id}/unlock")
    class UnlockTests {

        @Test
        @DisplayName("ロック解除で204を返す")
        void unlock_success_returns204() throws Exception {
            mockMvc.perform(patch(BASE_URL + "/1/unlock"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void unlock_notFound_returns404() throws Exception {
            org.mockito.Mockito.doThrow(ResourceNotFoundException.of("USER_NOT_FOUND", "ユーザー", 999L))
                    .when(userService).unlock(999L);

            mockMvc.perform(patch(BASE_URL + "/999/unlock"))
                    .andExpect(status().isNotFound());
        }
    }

    // ========== checkUserCodeExists ==========

    @Nested
    @DisplayName("GET /api/v1/master/users/exists")
    class ExistsTests {

        @Test
        @DisplayName("存在するコードでexists=trueを返す")
        void exists_true() throws Exception {
            when(rateLimiterService.tryConsumeCodeExists(any())).thenReturn(true);
            when(userService.existsByCode("USR001")).thenReturn(true);

            mockMvc.perform(get(BASE_URL + "/exists").param("code", "USR001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(true));
        }

        @Test
        @DisplayName("存在しないコードでexists=falseを返す")
        void exists_false() throws Exception {
            when(rateLimiterService.tryConsumeCodeExists(any())).thenReturn(true);
            when(userService.existsByCode("XXXX")).thenReturn(false);

            mockMvc.perform(get(BASE_URL + "/exists").param("code", "XXXX"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(false));
        }

        @Test
        @DisplayName("code未指定で400を返す")
        void exists_missingParam_returns400() throws Exception {
            mockMvc.perform(get(BASE_URL + "/exists"))
                    .andExpect(status().isBadRequest());
        }
    }

    // --- Helper ---

    private static User createUser(Long id, String code, String fullName) {
        User u = User.builder()
                .userCode(code)
                .fullName(fullName)
                .email(code.toLowerCase() + "@example.com")
                .passwordHash("$2a$12$dummyhash")
                .role("SYSTEM_ADMIN")
                .build();
        if (id != null) {
            u.setId(id);
            u.setCreatedAt(OffsetDateTime.now());
            u.setUpdatedAt(OffsetDateTime.now());
        }
        return u;
    }
}
