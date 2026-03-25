package com.wms.inventory.service;

import com.wms.inventory.entity.StocktakeHeader;
import com.wms.inventory.repository.StocktakeHeaderRepository;
import com.wms.inventory.repository.StocktakeLineRepository;
import com.wms.master.entity.Warehouse;
import com.wms.master.service.WarehouseService;
import com.wms.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.wms.inventory.entity.StocktakeLine;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StocktakeQueryService")
class StocktakeQueryServiceTest {

    @Mock private StocktakeHeaderRepository headerRepository;
    @Mock private StocktakeLineRepository lineRepository;
    @Mock private WarehouseService warehouseService;
    @InjectMocks private StocktakeQueryService service;

    @Test
    @DisplayName("正常系: 棚卸一覧を返す")
    void search_success() {
        when(warehouseService.findById(1L)).thenReturn(new Warehouse());
        StocktakeHeader h = StocktakeHeader.builder()
                .stocktakeNumber("ST-001").warehouseId(1L).status("STARTED")
                .stocktakeDate(LocalDate.of(2026, 3, 20))
                .startedAt(OffsetDateTime.now()).startedBy(10L).build();
        when(headerRepository.search(eq(1L), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(h)));

        Page<StocktakeHeader> result = service.search(1L, null, null, null, PageRequest.of(0, 20));
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("フィルタ指定で検索")
    void search_withFilters() {
        when(warehouseService.findById(1L)).thenReturn(new Warehouse());
        when(headerRepository.search(eq(1L), eq("STARTED"), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        Page<StocktakeHeader> result = service.search(1L, "STARTED",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), PageRequest.of(0, 20));
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("倉庫不存在の場合ResourceNotFoundException")
    void search_warehouseNotFound() {
        when(warehouseService.findById(999L))
                .thenThrow(new ResourceNotFoundException("WAREHOUSE_NOT_FOUND", "倉庫が見つかりません"));
        assertThatThrownBy(() -> service.search(999L, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findByIdWithLines: 正常系")
    void findByIdWithLines_success() {
        StocktakeHeader h = StocktakeHeader.builder().stocktakeNumber("ST-001").build();
        when(headerRepository.findByIdWithLines(1L)).thenReturn(Optional.of(h));
        assertThat(service.findByIdWithLines(1L).getStocktakeNumber()).isEqualTo("ST-001");
    }

    @Test
    @DisplayName("findByIdWithLines: 不存在の場合ResourceNotFoundException")
    void findByIdWithLines_notFound() {
        when(headerRepository.findByIdWithLines(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findByIdWithLines(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("countTotalLines/countCountedLines")
    void countLines() {
        when(lineRepository.countByHeaderId(1L)).thenReturn(10L);
        when(lineRepository.countCountedByHeaderId(1L)).thenReturn(7L);
        assertThat(service.countTotalLines(1L)).isEqualTo(10);
        assertThat(service.countCountedLines(1L)).isEqualTo(7);
    }

    @Test
    @DisplayName("findById: 正常系")
    void findById_success() {
        StocktakeHeader h = StocktakeHeader.builder().stocktakeNumber("ST-002").build();
        when(headerRepository.findById(2L)).thenReturn(Optional.of(h));
        assertThat(service.findById(2L).getStocktakeNumber()).isEqualTo("ST-002");
    }

    @Test
    @DisplayName("findById: 不存在の場合ResourceNotFoundException")
    void findById_notFound() {
        when(headerRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("棚卸が見つかりません");
    }

    @Test
    @DisplayName("searchLines: 正常系")
    void searchLines_success() {
        Page<StocktakeLine> page = new PageImpl<>(List.of());
        when(lineRepository.searchByHeader(eq(1L), eq(true), eq("A-"), any(Pageable.class)))
                .thenReturn(page);

        Page<StocktakeLine> result = service.searchLines(1L, true, "A-", PageRequest.of(0, 20));
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("searchLines: フィルタなしで全件検索")
    void searchLines_noFilter() {
        Page<StocktakeLine> page = new PageImpl<>(List.of());
        when(lineRepository.searchByHeader(eq(1L), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(page);

        Page<StocktakeLine> result = service.searchLines(1L, null, null, PageRequest.of(0, 20));
        assertThat(result).isNotNull();
    }
}
