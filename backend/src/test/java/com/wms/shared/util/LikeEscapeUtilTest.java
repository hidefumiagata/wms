package com.wms.shared.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LikeEscapeUtil: LIKE検索ワイルドカードエスケープ")
class LikeEscapeUtilTest {

    @Test
    @DisplayName("nullはnullを返す")
    void escape_null_returnsNull() {
        assertThat(LikeEscapeUtil.escape(null)).isNull();
    }

    @Test
    @DisplayName("ワイルドカードを含まない文字列はそのまま返す")
    void escape_noWildcard_returnsSame() {
        assertThat(LikeEscapeUtil.escape("ABC")).isEqualTo("ABC");
    }

    @Test
    @DisplayName("%がエスケープされる")
    void escape_percent_isEscaped() {
        assertThat(LikeEscapeUtil.escape("50%OFF")).isEqualTo("50\\%OFF");
    }

    @Test
    @DisplayName("_がエスケープされる")
    void escape_underscore_isEscaped() {
        assertThat(LikeEscapeUtil.escape("A_B")).isEqualTo("A\\_B");
    }

    @Test
    @DisplayName("バックスラッシュがエスケープされる")
    void escape_backslash_isEscaped() {
        assertThat(LikeEscapeUtil.escape("A\\B")).isEqualTo("A\\\\B");
    }

    @Test
    @DisplayName("複数のワイルドカードが同時にエスケープされる")
    void escape_multipleWildcards_allEscaped() {
        assertThat(LikeEscapeUtil.escape("%_\\")).isEqualTo("\\%\\_\\\\");
    }

    @Test
    @DisplayName("空文字列はそのまま返す")
    void escape_empty_returnsEmpty() {
        assertThat(LikeEscapeUtil.escape("")).isEqualTo("");
    }
}
