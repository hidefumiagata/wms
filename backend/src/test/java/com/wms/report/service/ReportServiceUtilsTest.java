package com.wms.report.service;

import com.wms.master.entity.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportServiceUtils")
class ReportServiceUtilsTest {

    @Nested
    @DisplayName("INBOUND_STATUS_LABELS")
    class StatusLabels {

        @Test
        @DisplayName("全7ステータスのラベルが定義されている")
        void allStatusesDefined() {
            assertThat(ReportServiceUtils.INBOUND_STATUS_LABELS).hasSize(7);
            assertThat(ReportServiceUtils.INBOUND_STATUS_LABELS.get("PLANNED")).isEqualTo("入荷予定");
            assertThat(ReportServiceUtils.INBOUND_STATUS_LABELS.get("CANCELLED")).isEqualTo("キャンセル");
        }
    }

    @Nested
    @DisplayName("todayFileDate")
    class TodayFileDate {

        @Test
        @DisplayName("yyyyMMdd形式の文字列を返す")
        void returnsFormattedDate() {
            String result = ReportServiceUtils.todayFileDate();
            assertThat(result).matches("\\d{8}");
        }
    }

    @Nested
    @DisplayName("getCurrentUserName")
    class GetCurrentUserName {

        @Test
        @DisplayName("認証情報がある場合はユーザー名を返す")
        void returnsUserName() {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("testUser", "password"));

            assertThat(ReportServiceUtils.getCurrentUserName()).isEqualTo("testUser");

            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("認証情報がない場合はsystemを返す")
        void returnsSystemWhenNoAuth() {
            SecurityContextHolder.clearContext();

            assertThat(ReportServiceUtils.getCurrentUserName()).isEqualTo("system");
        }
    }

    @Nested
    @DisplayName("getCaseQuantity")
    class GetCaseQuantity {

        @Test
        @DisplayName("商品がnullの場合は1を返す")
        void nullProduct_returnsOne() {
            assertThat(ReportServiceUtils.getCaseQuantity(null)).isEqualTo(1);
        }

        @Test
        @DisplayName("ケース入数が正の場合はその値を返す")
        void positiveCaseQuantity_returnsValue() {
            Product product = new Product();
            product.setCaseQuantity(10);
            assertThat(ReportServiceUtils.getCaseQuantity(product)).isEqualTo(10);
        }

        @Test
        @DisplayName("ケース入数が0の場合は0を返す")
        void zeroCaseQuantity_returnsZero() {
            Product product = new Product();
            product.setCaseQuantity(0);
            assertThat(ReportServiceUtils.getCaseQuantity(product)).isZero();
        }
    }

    @Nested
    @DisplayName("defaultFormat")
    class DefaultFormat {

        @Test
        @DisplayName("nullの場合はJSONを返す")
        void null_returnsJson() {
            assertThat(ReportServiceUtils.defaultFormat(null))
                    .isEqualTo(com.wms.generated.model.ReportFormat.JSON);
        }

        @Test
        @DisplayName("指定値がある場合はそのまま返す")
        void nonNull_returnsValue() {
            assertThat(ReportServiceUtils.defaultFormat(com.wms.generated.model.ReportFormat.PDF))
                    .isEqualTo(com.wms.generated.model.ReportFormat.PDF);
        }
    }

    @Nested
    @DisplayName("escapeLikePattern")
    class EscapeLikePattern {

        @Test
        @DisplayName("nullの場合はnullを返す")
        void null_returnsNull() {
            assertThat(ReportServiceUtils.escapeLikePattern(null)).isNull();
        }

        @Test
        @DisplayName("ワイルドカード文字をエスケープする")
        void escapesWildcards() {
            assertThat(ReportServiceUtils.escapeLikePattern("100%_test\\"))
                    .isEqualTo("100\\%\\_test\\\\");
        }
    }
}
