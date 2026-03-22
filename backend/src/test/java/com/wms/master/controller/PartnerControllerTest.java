package com.wms.master.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.generated.model.CreatePartnerRequest;
import com.wms.generated.model.PartnerType;
import com.wms.generated.model.ToggleActiveRequest;
import com.wms.generated.model.UpdatePartnerRequest;
import com.wms.master.entity.Partner;
import com.wms.master.service.PartnerService;
import com.wms.shared.exception.DuplicateResourceException;
import com.wms.shared.exception.OptimisticLockConflictException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.security.JwtAuthenticationFilter;
import com.wms.shared.security.JwtTokenProvider;
import com.wms.shared.security.RateLimiterService;
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

@WebMvcTest(PartnerController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("PartnerController")
class PartnerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PartnerService partnerService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    private static final String BASE_URL = "/api/v1/master/partners";

    // ========== listPartners ==========

    @Nested
    @DisplayName("GET /api/v1/master/partners")
    class ListTests {

        @Test
        @DisplayName("ページング形式で一覧を返す")
        void list_paged_returnsPageResponse() throws Exception {
            Partner p = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            var page = new PageImpl<>(List.of(p), PageRequest.of(0, 20), 1);
            when(partnerService.search(any(), any(), any(), any(), any())).thenReturn(page);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].partnerCode").value("SUP-001"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("all=trueでPII除外の軽量レスポンスを返す")
        void list_all_returnsSimpleWithoutPii() throws Exception {
            Partner p = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            p.setEmail("secret@example.com");
            p.setPhone("03-1234-5678");
            p.setAddress("東京都千代田区");
            p.setContactPerson("担当太郎");
            when(partnerService.findAllSimple(true)).thenReturn(List.of(p));

            mockMvc.perform(get(BASE_URL).param("all", "true").param("isActive", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].partnerCode").value("SUP-001"))
                    .andExpect(jsonPath("$.content[0].partnerType").value("SUPPLIER"))
                    .andExpect(jsonPath("$.content[0].isActive").value(true))
                    .andExpect(jsonPath("$.content[0].email").doesNotExist())
                    .andExpect(jsonPath("$.content[0].phone").doesNotExist())
                    .andExpect(jsonPath("$.content[0].address").doesNotExist())
                    .andExpect(jsonPath("$.content[0].contactPerson").doesNotExist())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("all=trueで空リストを返す場合totalPages=0")
        void list_all_emptyResult_totalPagesIsZero() throws Exception {
            when(partnerService.findAllSimple(null)).thenReturn(List.of());

            mockMvc.perform(get(BASE_URL).param("all", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalPages").value(0));
        }

        @Test
        @DisplayName("partnerTypeフィルタを指定して検索できる")
        void list_paged_withPartnerType() throws Exception {
            Partner p = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            var page = new PageImpl<>(List.of(p), PageRequest.of(0, 20), 1);
            when(partnerService.search(any(), any(), eq("SUPPLIER"), any(), any())).thenReturn(page);

            mockMvc.perform(get(BASE_URL).param("partnerType", "SUPPLIER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("descソートで一覧を返す")
        void list_paged_descSort() throws Exception {
            Partner p = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            var page = new PageImpl<>(List.of(p), PageRequest.of(0, 20), 1);
            when(partnerService.search(any(), any(), any(), any(), any())).thenReturn(page);

            mockMvc.perform(get(BASE_URL).param("sort", "partnerName,desc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("ソート方向なしでasc扱いになる")
        void list_paged_sortWithoutDirection() throws Exception {
            Partner p = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            var page = new PageImpl<>(List.of(p), PageRequest.of(0, 20), 1);
            when(partnerService.search(any(), any(), any(), any(), any())).thenReturn(page);

            mockMvc.perform(get(BASE_URL).param("sort", "partnerCode"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(partnerService).search(any(), any(), any(), any(), pageableCaptor.capture());
            var order = pageableCaptor.getValue().getSort().getOrderFor("partnerCode");
            assertThat(order).isNotNull();
            assertThat(order.getDirection().isAscending()).isTrue();
        }

        @Test
        @DisplayName("許可されていないソートプロパティはデフォルトにフォールバック")
        void list_paged_invalidSortProperty_fallbackToDefault() throws Exception {
            var page = new PageImpl<Partner>(List.of(), PageRequest.of(0, 20), 0);
            when(partnerService.search(any(), any(), any(), any(), any())).thenReturn(page);

            mockMvc.perform(get(BASE_URL).param("sort", "malicious_column,asc"))
                    .andExpect(status().isOk());

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(partnerService).search(any(), any(), any(), any(), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getSort().getOrderFor("partnerCode")).isNotNull();
        }

        @Test
        @DisplayName("ページサイズが上限100に制限される")
        void list_paged_sizeIsCappedAt100() throws Exception {
            var page = new PageImpl<Partner>(List.of(), PageRequest.of(0, 100), 0);
            when(partnerService.search(any(), any(), any(), any(), any())).thenReturn(page);

            mockMvc.perform(get(BASE_URL).param("size", "9999"))
                    .andExpect(status().isOk());

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(partnerService).search(any(), any(), any(), any(), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("空ページを返す")
        void list_paged_emptyResult() throws Exception {
            var page = new PageImpl<Partner>(List.of(), PageRequest.of(0, 20), 0);
            when(partnerService.search(any(), any(), any(), any(), any())).thenReturn(page);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    // ========== createPartner ==========

    @Nested
    @DisplayName("POST /api/v1/master/partners")
    class CreateTests {

        @Test
        @DisplayName("正常な登録リクエストで201を返す")
        void create_success_returns201() throws Exception {
            Partner created = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            when(partnerService.create(any(Partner.class))).thenReturn(created);

            CreatePartnerRequest request = new CreatePartnerRequest()
                    .partnerCode("SUP-001")
                    .partnerName("仕入先A")
                    .partnerNameKana("シイレサキエー")
                    .partnerType(PartnerType.SUPPLIER);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v1/master/partners/1"))
                    .andExpect(jsonPath("$.partnerCode").value("SUP-001"))
                    .andExpect(jsonPath("$.partnerName").value("仕入先A"))
                    .andExpect(jsonPath("$.partnerType").value("SUPPLIER"));
        }

        @Test
        @DisplayName("重複コードで409を返す")
        void create_duplicateCode_returns409() throws Exception {
            when(partnerService.create(any(Partner.class)))
                    .thenThrow(new DuplicateResourceException("DUPLICATE_CODE", "重複"));

            CreatePartnerRequest request = new CreatePartnerRequest()
                    .partnerCode("SUP-001")
                    .partnerName("仕入先A")
                    .partnerNameKana("シイレサキエー")
                    .partnerType(PartnerType.SUPPLIER);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("必須項目未設定で400を返す")
        void create_missingRequired_returns400() throws Exception {
            CreatePartnerRequest request = new CreatePartnerRequest();

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ========== getPartner ==========

    @Nested
    @DisplayName("GET /api/v1/master/partners/{id}")
    class GetTests {

        @Test
        @DisplayName("存在するIDで200を返す")
        void get_exists_returns200() throws Exception {
            Partner p = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            when(partnerService.findById(1L)).thenReturn(p);

            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.partnerCode").value("SUP-001"))
                    .andExpect(jsonPath("$.partnerType").value("SUPPLIER"));
        }

        @Test
        @DisplayName("日時がnullの場合もレスポンスを返せる")
        void get_nullTimestamps_returns200() throws Exception {
            Partner p = createPartnerWithoutTimestamps(2L, "CUS-001", "顧客A", "CUSTOMER");
            when(partnerService.findById(2L)).thenReturn(p);

            mockMvc.perform(get(BASE_URL + "/2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.partnerCode").value("CUS-001"))
                    .andExpect(jsonPath("$.createdAt").doesNotExist());
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void get_notFound_returns404() throws Exception {
            when(partnerService.findById(999L))
                    .thenThrow(ResourceNotFoundException.of("PARTNER_NOT_FOUND", "取引先", 999L));

            mockMvc.perform(get(BASE_URL + "/999"))
                    .andExpect(status().isNotFound());
        }
    }

    // ========== updatePartner ==========

    @Nested
    @DisplayName("PUT /api/v1/master/partners/{id}")
    class UpdateTests {

        @Test
        @DisplayName("正常な更新で200を返す")
        void update_success_returns200() throws Exception {
            Partner updated = createPartner(1L, "SUP-001", "仕入先A（更新）", "SUPPLIER");
            when(partnerService.update(any(com.wms.master.service.UpdatePartnerCommand.class)))
                    .thenReturn(updated);

            UpdatePartnerRequest request = new UpdatePartnerRequest()
                    .partnerName("仕入先A（更新）")
                    .partnerNameKana("シイレサキエーコウシン")
                    .partnerType(PartnerType.SUPPLIER)
                    .version(0);

            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.partnerName").value("仕入先A（更新）"));
        }

        @Test
        @DisplayName("楽観的ロック競合で409を返す")
        void update_conflict_returns409() throws Exception {
            when(partnerService.update(any(com.wms.master.service.UpdatePartnerCommand.class)))
                    .thenThrow(new OptimisticLockConflictException("OPTIMISTIC_LOCK_CONFLICT", "競合"));

            UpdatePartnerRequest request = new UpdatePartnerRequest()
                    .partnerName("名前")
                    .partnerNameKana("ナマエ")
                    .partnerType(PartnerType.SUPPLIER)
                    .version(0);

            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("必須項目未設定で400を返す")
        void update_missingRequired_returns400() throws Exception {
            UpdatePartnerRequest request = new UpdatePartnerRequest();

            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("存在しないIDで404を返す")
        void update_notFound_returns404() throws Exception {
            when(partnerService.update(any(com.wms.master.service.UpdatePartnerCommand.class)))
                    .thenThrow(ResourceNotFoundException.of("PARTNER_NOT_FOUND", "取引先", 999L));

            UpdatePartnerRequest request = new UpdatePartnerRequest()
                    .partnerName("名前")
                    .partnerNameKana("ナマエ")
                    .partnerType(PartnerType.SUPPLIER)
                    .version(0);

            mockMvc.perform(put(BASE_URL + "/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    // ========== togglePartnerActive ==========

    @Nested
    @DisplayName("PATCH /api/v1/master/partners/{id}/deactivate")
    class ToggleTests {

        @Test
        @DisplayName("無効化で200を返す")
        void toggle_deactivate_returns200() throws Exception {
            Partner p = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            p.deactivate();
            when(partnerService.toggleActive(eq(1L), eq(false), eq(0))).thenReturn(p);

            ToggleActiveRequest request = new ToggleActiveRequest()
                    .isActive(false)
                    .version(0);

            mockMvc.perform(patch(BASE_URL + "/1/deactivate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isActive").value(false));
        }

        @Test
        @DisplayName("有効化で200を返す")
        void toggle_activate_returns200() throws Exception {
            Partner p = createPartner(1L, "SUP-001", "仕入先A", "SUPPLIER");
            when(partnerService.toggleActive(eq(1L), eq(true), eq(0))).thenReturn(p);

            ToggleActiveRequest request = new ToggleActiveRequest()
                    .isActive(true)
                    .version(0);

            mockMvc.perform(patch(BASE_URL + "/1/deactivate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isActive").value(true));
        }

        @Test
        @DisplayName("楽観的ロック競合で409を返す")
        void toggle_conflict_returns409() throws Exception {
            when(partnerService.toggleActive(eq(1L), eq(false), eq(0)))
                    .thenThrow(new OptimisticLockConflictException("OPTIMISTIC_LOCK_CONFLICT", "競合"));

            ToggleActiveRequest request = new ToggleActiveRequest()
                    .isActive(false)
                    .version(0);

            mockMvc.perform(patch(BASE_URL + "/1/deactivate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("必須項目未設定で400を返す")
        void toggle_missingRequired_returns400() throws Exception {
            ToggleActiveRequest request = new ToggleActiveRequest();

            mockMvc.perform(patch(BASE_URL + "/1/deactivate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ========== checkPartnerCodeExists ==========

    @Nested
    @DisplayName("GET /api/v1/master/partners/exists")
    class ExistsTests {

        @Test
        @DisplayName("存在するコードでexists=trueを返す")
        void exists_true() throws Exception {
            when(partnerService.existsByCode("SUP-001")).thenReturn(true);

            mockMvc.perform(get(BASE_URL + "/exists").param("partnerCode", "SUP-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(true));
        }

        @Test
        @DisplayName("存在しないコードでexists=falseを返す")
        void exists_false() throws Exception {
            when(partnerService.existsByCode("XXXX")).thenReturn(false);

            mockMvc.perform(get(BASE_URL + "/exists").param("partnerCode", "XXXX"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(false));
        }

        @Test
        @DisplayName("partnerCode未指定で400を返す")
        void exists_missingParam_returns400() throws Exception {
            mockMvc.perform(get(BASE_URL + "/exists"))
                    .andExpect(status().isBadRequest());
        }
    }

    // --- Helper ---

    private static Partner createPartnerWithoutTimestamps(Long id, String code, String name, String type) {
        Partner p = new Partner();
        p.setPartnerCode(code);
        p.setPartnerName(name);
        p.setPartnerType(com.wms.master.entity.PartnerType.valueOf(type));
        if (id != null) {
            try {
                var field = com.wms.shared.entity.BaseEntity.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(p, id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return p;
    }

    private static Partner createPartner(Long id, String code, String name, String type) {
        Partner p = new Partner();
        p.setPartnerCode(code);
        p.setPartnerName(name);
        p.setPartnerType(com.wms.master.entity.PartnerType.valueOf(type));
        if (id != null) {
            try {
                var field = com.wms.shared.entity.BaseEntity.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(p, id);
                var createdAtField = com.wms.shared.entity.BaseEntity.class.getDeclaredField("createdAt");
                createdAtField.setAccessible(true);
                createdAtField.set(p, OffsetDateTime.now());
                var updatedAtField = com.wms.shared.entity.BaseEntity.class.getDeclaredField("updatedAt");
                updatedAtField.setAccessible(true);
                updatedAtField.set(p, OffsetDateTime.now());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return p;
    }
}
