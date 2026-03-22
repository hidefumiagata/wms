package com.wms.inbound.service;

import com.wms.inbound.entity.InboundSlip;
import com.wms.inbound.repository.InboundSlipRepository;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.WarehouseService;
import com.wms.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InboundSlipService")
class InboundSlipServiceTest {

    @Mock
    private InboundSlipRepository inboundSlipRepository;

    @Mock
    private WarehouseService warehouseService;

    @InjectMocks
    private InboundSlipService inboundSlipService;

    @Nested
    @DisplayName("search")
    class SearchTests {

        @Test
        @DisplayName("倉庫存在チェック後に検索結果を返す")
        void search_returnsPage() {
            Warehouse wh = new Warehouse();
            when(warehouseService.findById(1L)).thenReturn(wh);

            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260320-0001")
                    .status("PLANNED")
                    .build();
            Page<InboundSlip> page = new PageImpl<>(List.of(slip));
            when(inboundSlipRepository.search(
                    eq(1L), any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(page);

            Page<InboundSlip> result = inboundSlipService.search(
                    1L, null, null, null, null, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getSlipNumber()).isEqualTo("INB-20260320-0001");
            verify(warehouseService).findById(1L);
        }

        @Test
        @DisplayName("倉庫が存在しない場合ResourceNotFoundExceptionをスローする")
        void search_warehouseNotFound_throws() {
            when(warehouseService.findById(999L))
                    .thenThrow(new ResourceNotFoundException("WAREHOUSE_NOT_FOUND", "倉庫 が見つかりません (id=999)"));

            assertThatThrownBy(() -> inboundSlipService.search(
                    999L, null, null, null, null, null, PageRequest.of(0, 20)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("倉庫");
        }

        @Test
        @DisplayName("ステータスフィルタ付きで検索できる")
        void search_withStatusFilter() {
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            when(inboundSlipRepository.search(
                    eq(1L), any(), eq(List.of("PLANNED", "CONFIRMED")), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<InboundSlip> result = inboundSlipService.search(
                    1L, null, List.of("PLANNED", "CONFIRMED"), null, null, null,
                    PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("日付範囲フィルタ付きで検索できる")
        void search_withDateRange() {
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            LocalDate from = LocalDate.of(2026, 3, 1);
            LocalDate to = LocalDate.of(2026, 3, 31);
            when(inboundSlipRepository.search(
                    eq(1L), any(), any(), eq(from), eq(to), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<InboundSlip> result = inboundSlipService.search(
                    1L, null, null, from, to, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("仕入先IDフィルタ付きで検索できる")
        void search_withPartnerId() {
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            when(inboundSlipRepository.search(
                    eq(1L), any(), any(), any(), any(), eq(5L), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<InboundSlip> result = inboundSlipService.search(
                    1L, null, null, null, null, 5L, PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("伝票番号フィルタ付きで検索できる")
        void search_withSlipNumber() {
            when(warehouseService.findById(1L)).thenReturn(new Warehouse());
            when(inboundSlipRepository.search(
                    eq(1L), eq("INB-2026"), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<InboundSlip> result = inboundSlipService.search(
                    1L, "INB-2026", null, null, null, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findById")
    class FindByIdTests {

        @Test
        @DisplayName("存在するIDで伝票を返す")
        void findById_exists_returnsSlip() {
            InboundSlip slip = InboundSlip.builder()
                    .slipNumber("INB-20260320-0001")
                    .status("PLANNED")
                    .build();
            when(inboundSlipRepository.findById(1L)).thenReturn(Optional.of(slip));

            InboundSlip result = inboundSlipService.findById(1L);

            assertThat(result.getSlipNumber()).isEqualTo("INB-20260320-0001");
        }

        @Test
        @DisplayName("存在しないIDでResourceNotFoundExceptionをスローする")
        void findById_notExists_throws() {
            when(inboundSlipRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> inboundSlipService.findById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("入荷伝票");
        }
    }
}
