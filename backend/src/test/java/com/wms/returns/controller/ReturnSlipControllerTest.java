package com.wms.returns.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.generated.model.CreateReturnRequest;
import com.wms.generated.model.ReturnReason;
import com.wms.generated.model.ReturnType;
import com.wms.returns.entity.ReturnSlip;
import com.wms.returns.service.ReturnSlipService;
import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.shared.security.JwtAuthenticationFilter;
import com.wms.shared.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReturnSlipController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReturnSlipControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ReturnSlipService returnSlipService;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;

    private static void setField(Object obj, String fieldName, Object value) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
                return;
            } catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        throw new RuntimeException("Field not found: " + fieldName);
    }

    private ReturnSlip buildReturnSlip() {
        ReturnSlip slip = ReturnSlip.builder()
                .returnNumber("RTN-S-20260318-0001")
                .returnType("INVENTORY")
                .status("COMPLETED")
                .warehouseId(1L)
                .warehouseCode("WH-001")
                .warehouseName("東京DC")
                .productId(10L)
                .productCode("PRD-0001")
                .productName("テスト商品A")
                .quantity(5)
                .unitType("CASE")
                .locationId(30L)
                .locationCode("A-01-01")
                .partnerId(20L)
                .partnerCode("SUP-0001")
                .partnerName("株式会社ABC商事")
                .returnReason("QUALITY_DEFECT")
                .returnReasonNote("外装に破れあり")
                .relatedSlipNumber("INB-20260318-0001")
                .lotNumber("LOT-001")
                .expiryDate(LocalDate.of(2026, 12, 31))
                .returnDate(LocalDate.of(2026, 3, 18))
                .build();
        setField(slip, "id", 1L);
        setField(slip, "createdAt", OffsetDateTime.now());
        setField(slip, "createdBy", 100L);
        setField(slip, "updatedAt", OffsetDateTime.now());
        setField(slip, "updatedBy", 100L);
        return slip;
    }

    @Nested
    @DisplayName("createReturn")
    class CreateReturn {

        @Test
        @DisplayName("正常系: 201 Created")
        void createReturn_success_returns201() throws Exception {
            ReturnSlip slip = buildReturnSlip();
            when(returnSlipService.create(any(CreateReturnRequest.class))).thenReturn(slip);
            when(returnSlipService.resolveUserName(100L)).thenReturn("山田 太郎");

            CreateReturnRequest request = new CreateReturnRequest()
                    .returnType(ReturnType.INVENTORY)
                    .productId(10L)
                    .quantity(5)
                    .unitType(CreateReturnRequest.UnitTypeEnum.CASE)
                    .locationId(30L)
                    .returnReason(ReturnReason.QUALITY_DEFECT)
                    .returnReasonNote("外装に破れあり");

            mockMvc.perform(post("/api/v1/returns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.slipNumber").value("RTN-S-20260318-0001"))
                    .andExpect(jsonPath("$.returnType").value("INVENTORY"))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.warehouseCode").value("WH-001"))
                    .andExpect(jsonPath("$.productCode").value("PRD-0001"))
                    .andExpect(jsonPath("$.quantity").value(5))
                    .andExpect(jsonPath("$.locationCode").value("A-01-01"))
                    .andExpect(jsonPath("$.partnerCode").value("SUP-0001"))
                    .andExpect(jsonPath("$.returnReason").value("QUALITY_DEFECT"))
                    .andExpect(jsonPath("$.createdByName").value("山田 太郎"));
        }

        @Test
        @DisplayName("バリデーションエラー: 必須項目不足 400")
        void createReturn_missingRequired_returns400() throws Exception {
            String body = "{}";

            mockMvc.perform(post("/api/v1/returns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("商品Not Found: 404")
        void createReturn_productNotFound_returns404() throws Exception {
            when(returnSlipService.create(any(CreateReturnRequest.class)))
                    .thenThrow(new ResourceNotFoundException("PRODUCT_NOT_FOUND",
                            "商品が見つかりません"));

            CreateReturnRequest request = new CreateReturnRequest()
                    .returnType(ReturnType.INBOUND)
                    .productId(999L)
                    .quantity(1)
                    .unitType(CreateReturnRequest.UnitTypeEnum.CASE)
                    .returnReason(ReturnReason.DAMAGED);

            mockMvc.perform(post("/api/v1/returns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("業務ルール違反: 422")
        void createReturn_businessRuleViolation_returns422() throws Exception {
            when(returnSlipService.create(any(CreateReturnRequest.class)))
                    .thenThrow(new BusinessRuleViolationException(
                            "RETURN_ALLOCATED_INVENTORY", "引当済み在庫は返品できません"));

            CreateReturnRequest request = new CreateReturnRequest()
                    .returnType(ReturnType.INVENTORY)
                    .productId(10L)
                    .quantity(5)
                    .unitType(CreateReturnRequest.UnitTypeEnum.CASE)
                    .locationId(30L)
                    .returnReason(ReturnReason.QUALITY_DEFECT);

            mockMvc.perform(post("/api/v1/returns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Nested
    @DisplayName("getReturns")
    class GetReturns {

        @Test
        @DisplayName("正常系: 200 OK with content")
        void getReturns_success_returns200() throws Exception {
            ReturnSlip slip = buildReturnSlip();
            Pageable pageable = PageRequest.of(0, 20);
            when(returnSlipService.search(eq(1L), eq(null), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(slip), pageable, 1));
            when(returnSlipService.resolveUserName(100L)).thenReturn("山田 太郎");

            mockMvc.perform(get("/api/v1/returns")
                            .param("warehouseId", "1")
                            .param("page", "0")
                            .param("size", "20")
                            .param("sort", "returnDate,desc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].slipNumber").value("RTN-S-20260318-0001"))
                    .andExpect(jsonPath("$.content[0].returnType").value("INVENTORY"))
                    .andExpect(jsonPath("$.content[0].productCode").value("PRD-0001"))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test
        @DisplayName("正常系: 空結果")
        void getReturns_empty_returns200() throws Exception {
            Pageable pageable = PageRequest.of(0, 20);
            when(returnSlipService.search(eq(1L), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            mockMvc.perform(get("/api/v1/returns")
                            .param("warehouseId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("倉庫Not Found: 404")
        void getReturns_warehouseNotFound_returns404() throws Exception {
            when(returnSlipService.search(eq(999L), any(), any(), any(), any(), any(), any(), any()))
                    .thenThrow(new ResourceNotFoundException("WAREHOUSE_NOT_FOUND",
                            "倉庫が見つかりません"));

            mockMvc.perform(get("/api/v1/returns")
                            .param("warehouseId", "999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("不正なソートプロパティ: デフォルトにフォールバック")
        void getReturns_invalidSortProperty_fallbackToDefault() throws Exception {
            Pageable pageable = PageRequest.of(0, 20);
            when(returnSlipService.search(eq(1L), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            mockMvc.perform(get("/api/v1/returns")
                            .param("warehouseId", "1")
                            .param("sort", "invalidField,asc"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ソート方向なし: ASCデフォルト")
        void getReturns_sortWithoutDirection_usesAsc() throws Exception {
            Pageable pageable = PageRequest.of(0, 20);
            when(returnSlipService.search(eq(1L), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            mockMvc.perform(get("/api/v1/returns")
                            .param("warehouseId", "1")
                            .param("sort", "returnDate"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ソートASC指定")
        void getReturns_sortAsc_success() throws Exception {
            Pageable pageable = PageRequest.of(0, 20);
            when(returnSlipService.search(eq(1L), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            mockMvc.perform(get("/api/v1/returns")
                            .param("warehouseId", "1")
                            .param("sort", "createdAt,asc"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("フィルタ条件付き検索")
        void getReturns_withFilters_returns200() throws Exception {
            Pageable pageable = PageRequest.of(0, 20);
            when(returnSlipService.search(eq(1L), eq("INVENTORY"),
                    eq(LocalDate.of(2026, 3, 1)), eq(LocalDate.of(2026, 3, 31)),
                    eq("QUALITY_DEFECT"), eq(10L), eq(20L), any()))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            mockMvc.perform(get("/api/v1/returns")
                            .param("warehouseId", "1")
                            .param("returnType", "INVENTORY")
                            .param("returnDateFrom", "2026-03-01")
                            .param("returnDateTo", "2026-03-31")
                            .param("productId", "10")
                            .param("partnerId", "20")
                            .param("returnReason", "QUALITY_DEFECT"))
                    .andExpect(status().isOk());
        }
    }
}
