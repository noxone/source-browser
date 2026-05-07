package com.hlag.sourceviewer.domain.model.identifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class GitProviderGroupValueObjectUnitTest {

    // ── GitProviderGroupIdentifier ────────────────────────────────────────────

    @Test
    void gitProviderGroupIdentifier_accepts_positive_value() {
        var identifier = new GitProviderGroupIdentifier(1L);
        assertThat(identifier.value()).isEqualTo(1L);
    }

    @Test
    void gitProviderGroupIdentifier_implements_value_object() {
        assertThat(new GitProviderGroupIdentifier(1L)).isInstanceOf(ValueObject.class);
    }

    @Test
    void gitProviderGroupIdentifier_rejects_null() {
        assertThatThrownBy(() -> new GitProviderGroupIdentifier(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gitProviderGroupIdentifier_rejects_zero_and_negative_values() {
        assertThatThrownBy(() -> new GitProviderGroupIdentifier(0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GitProviderGroupIdentifier(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── GroupPath ─────────────────────────────────────────────────────────────

    @Test
    void groupPath_accepts_simple_path() {
        var path = new GroupPath("my-org");
        assertThat(path.value()).isEqualTo("my-org");
    }

    @Test
    void groupPath_accepts_nested_path() {
        var path = new GroupPath("my-org/my-subgroup");
        assertThat(path.value()).isEqualTo("my-org/my-subgroup");
    }

    @Test
    void groupPath_implements_value_object() {
        assertThat(new GroupPath("my-org")).isInstanceOf(ValueObject.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void groupPath_rejects_blank_values(String blank) {
        assertThatThrownBy(() -> new GroupPath(blank))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void groupPath_rejects_null() {
        assertThatThrownBy(() -> new GroupPath(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── GitProviderType ───────────────────────────────────────────────────────

    @Test
    void gitProviderType_has_gitlab_and_github() {
        assertThat(GitProviderType.values()).contains(GitProviderType.GITLAB, GitProviderType.GITHUB);
    }

    @Test
    void gitProviderType_can_be_parsed_from_string() {
        assertThat(GitProviderType.valueOf("GITLAB")).isEqualTo(GitProviderType.GITLAB);
        assertThat(GitProviderType.valueOf("GITHUB")).isEqualTo(GitProviderType.GITHUB);
    }
}
