package com.wms.report.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * レポートのメタデータ。個別レポートサービスが生成し、
 * ReportExportService に渡してフォーマット切替（JSON/CSV/PDF）を行う。
 */
public record ReportMeta(
        String reportTitle,
        String templateName,
        String fileSlug,
        String warehouseName,
        String userName,
        String conditionsSummary,
        String[] csvHeaders,
        Function<Object, String[]> csvRowMapper
) {

    private static final DateTimeFormatter PRINT_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    /**
     * Thymeleaf テンプレートに渡す変数 Map を生成する。
     */
    public Map<String, Object> toTemplateVariables(List<?> data) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("reportTitle", reportTitle);
        vars.put("printDate", LocalDateTime.now(JST).format(PRINT_DATE_FMT));
        vars.put("warehouseName", warehouseName);
        vars.put("userName", userName);
        vars.put("conditionsSummary", conditionsSummary);
        vars.put("items", data);
        return vars;
    }
}
