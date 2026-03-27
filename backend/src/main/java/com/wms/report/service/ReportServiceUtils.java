package com.wms.report.service;

import com.wms.inbound.entity.InboundSlipLine;
import com.wms.master.entity.Product;
import com.wms.master.repository.ProductRepository;
import org.springframework.security.core.context.SecurityContextHolder;

import com.wms.generated.model.ReportFormat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * レポートサービス共通ユーティリティ。
 * STATUS_LABELS、商品マスタバルクロード、日付フォーマット等を集約する。
 */
final class ReportServiceUtils {

    private ReportServiceUtils() {
    }

    static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 入荷ステータス → 日本語ラベル */
    static final Map<String, String> INBOUND_STATUS_LABELS = Map.of(
            "PLANNED", "入荷予定",
            "CONFIRMED", "確定",
            "INSPECTING", "検品中",
            "INSPECTED", "検品済",
            "PARTIAL_STORED", "一部入庫",
            "STORED", "入庫完了",
            "CANCELLED", "キャンセル"
    );

    /** 出荷ステータス → 日本語ラベル */
    static final Map<String, String> OUTBOUND_STATUS_LABELS = Map.ofEntries(
            Map.entry("DRAFT", "下書き"),
            Map.entry("PENDING", "受注済"),
            Map.entry("ORDERED", "受注"),
            Map.entry("PARTIAL_ALLOCATED", "一部引当"),
            Map.entry("ALLOCATED", "引当完了"),
            Map.entry("PICKING", "ピッキング中"),
            Map.entry("PICKING_COMPLETED", "ピッキング完了"),
            Map.entry("INSPECTING", "出荷検品中"),
            Map.entry("SHIPPED", "出荷完了"),
            Map.entry("CANCELLED", "キャンセル")
    );

    /** ピッキング指示ステータス → 日本語ラベル */
    static final Map<String, String> PICKING_STATUS_LABELS = Map.of(
            "DRAFT", "作成済",
            "ACTIVE", "作業中",
            "COMPLETED", "完了",
            "CANCELLED", "キャンセル"
    );

    /** formatがnullの場合はJSONをデフォルトとする */
    static ReportFormat defaultFormat(ReportFormat format) {
        return format != null ? format : ReportFormat.JSON;
    }

    /** 現在のJST日付をファイル名用にフォーマットする（例: "20260327"） */
    static String todayFileDate() {
        return LocalDate.now(JST).format(FILE_DATE_FMT);
    }

    /** SecurityContextから現在のユーザー名を取得する */
    static String getCurrentUserName() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }

    /** 明細行の商品IDからProductマスタをバルクロードしてMapで返す */
    static Map<Long, Product> loadProductMap(
            List<InboundSlipLine> lines, ProductRepository productRepository) {
        List<Long> productIds = lines.stream()
                .map(InboundSlipLine::getProductId)
                .distinct()
                .toList();
        return productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    /** LIKE句のワイルドカード文字をエスケープする */
    static String escapeLikePattern(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Productからケース入数を安全に取得する（null/0の場合は1を返す） */
    static int getCaseQuantity(Product product) {
        if (product == null) {
            return 1;
        }
        return product.getCaseQuantity() > 0 ? product.getCaseQuantity() : 0;
    }
}
