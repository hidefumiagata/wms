package com.wms.report.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportMetaTest {

    @Test
    @DisplayName("toTemplateVariables がレポートメタデータと items を含む Map を返す")
    void toTemplateVariables_containsAllFields() {
        ReportMeta meta = new ReportMeta(
                "テストレポート", "test-template", "test_20260327",
                "テスト倉庫", "admin", "期間: 2026-03-01 ～ 2026-03-27",
                new String[]{"ID"}, obj -> new String[]{obj.toString()}
        );

        List<String> data = List.of("item1", "item2");
        Map<String, Object> vars = meta.toTemplateVariables(data);

        assertThat(vars).containsEntry("reportTitle", "テストレポート");
        assertThat(vars).containsEntry("warehouseName", "テスト倉庫");
        assertThat(vars).containsEntry("userName", "admin");
        assertThat(vars).containsEntry("conditionsSummary", "期間: 2026-03-01 ～ 2026-03-27");
        assertThat(vars).containsEntry("items", data);
        assertThat(vars).containsKey("printDate");
        // printDate は yyyy-MM-dd HH:mm 形式
        assertThat((String) vars.get("printDate")).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");
    }

    @Test
    @DisplayName("extraTemplateVars が設定されている場合、vars にマージされる")
    void toTemplateVariables_withExtraVars_mergesIntoResult() {
        Map<String, Object> extraVars = Map.of("stocktakeNumber", "ST-001", "diffCount", 3);
        ReportMeta meta = new ReportMeta(
                "テスト", "t", "f", "w", "u", null,
                new String[]{}, obj -> new String[]{}, extraVars
        );

        Map<String, Object> vars = meta.toTemplateVariables(List.of());

        assertThat(vars).containsEntry("stocktakeNumber", "ST-001");
        assertThat(vars).containsEntry("diffCount", 3);
        assertThat(vars).containsEntry("reportTitle", "テスト");
    }

    @Test
    @DisplayName("extraTemplateVars が null の場合でもエラーにならない")
    void toTemplateVariables_nullExtraVars_noError() {
        ReportMeta meta = new ReportMeta(
                "テスト", "t", "f", "w", "u", null,
                new String[]{}, obj -> new String[]{}, null
        );

        Map<String, Object> vars = meta.toTemplateVariables(List.of());

        assertThat(vars).containsEntry("reportTitle", "テスト");
        assertThat(vars).containsKey("printDate");
    }

    @Test
    @DisplayName("toTemplateVariables の printDate が JST でフォーマットされる")
    void toTemplateVariables_printDateIsJst() {
        ReportMeta meta = new ReportMeta(
                "テスト", "t", "f", "w", "u", null,
                new String[]{}, obj -> new String[]{}
        );

        Map<String, Object> vars = meta.toTemplateVariables(List.of());

        String printDate = (String) vars.get("printDate");
        assertThat(printDate).isNotNull();
        assertThat(printDate).hasSize(16); // yyyy-MM-dd HH:mm
    }
}
