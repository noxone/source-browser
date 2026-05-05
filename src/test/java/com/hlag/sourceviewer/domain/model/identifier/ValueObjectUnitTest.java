package com.hlag.sourceviewer.domain.model.identifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class ValueObjectUnitTest {

    // ── SymbolIdentifier ──────────────────────────────────────────────────────

    @Test
    void symbolIdentifier_akzeptiert_positive_werte() {
        var identifier = new SymbolIdentifier(42L);
        assertThat(identifier.value()).isEqualTo(42L);
    }

    @Test
    void symbolIdentifier_lehnt_null_ab() {
        assertThatThrownBy(() -> new SymbolIdentifier(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void symbolIdentifier_lehnt_null_und_negative_werte_ab() {
        assertThatThrownBy(() -> new SymbolIdentifier(0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SymbolIdentifier(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── LineNumber ────────────────────────────────────────────────────────────

    @Test
    void lineNumber_akzeptiert_zeilennummer_eins() {
        var line = new LineNumber(1);
        assertThat(line.value()).isEqualTo(1);
    }

    @Test
    void lineNumber_lehnt_null_und_werte_kleiner_eins_ab() {
        assertThatThrownBy(() -> new LineNumber(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LineNumber(-5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── CommitSha ─────────────────────────────────────────────────────────────

    @Test
    void commitSha_akzeptiert_vollstaendigen_sha() {
        var sha = new CommitSha("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2");
        assertThat(sha.value()).hasSize(40);
        assertThat(sha.shortForm()).hasSize(7);
    }

    @Test
    void commitSha_akzeptiert_kurzen_sha() {
        var sha = new CommitSha("a1b2c3d");
        assertThat(sha.shortForm()).isEqualTo("a1b2c3d");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "xyz", "ZZZZZZZZ"})
    void commitSha_lehnt_ungueltige_formate_ab(String ungueltig) {
        assertThatThrownBy(() -> new CommitSha(ungueltig))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── FilePath ──────────────────────────────────────────────────────────────

    @Test
    void filePath_erkennt_java_dateien() {
        var path = new FilePath("src/main/java/eu/noxone/Foo.java");
        assertThat(path.isJavaFile()).isTrue();
        assertThat(path.extension()).isEqualTo("java");
    }

    @Test
    void filePath_erkennt_nicht_java_dateien() {
        var path = new FilePath("src/main/resources/application.properties");
        assertThat(path.isJavaFile()).isFalse();
        assertThat(path.extension()).isEqualTo("properties");
    }

    // ── ValueObject-Interface ─────────────────────────────────────────────────

    @Test
    void alle_wrapper_implementieren_value_object_interface() {
        assertThat(new SymbolIdentifier(1L)).isInstanceOf(ValueObject.class);
        assertThat(new FileIdentifier(1L)).isInstanceOf(ValueObject.class);
        assertThat(new LineNumber(1)).isInstanceOf(ValueObject.class);
        assertThat(new ColumnNumber(1)).isInstanceOf(ValueObject.class);
        assertThat(new CommitSha("a1b2c3d")).isInstanceOf(ValueObject.class);
        assertThat(new BranchName("main")).isInstanceOf(ValueObject.class);
        assertThat(new SimpleName("MyClass")).isInstanceOf(ValueObject.class);
        assertThat(new Description("Ein Text")).isInstanceOf(ValueObject.class);
        assertThat(new ErrorMessage("Fehler")).isInstanceOf(ValueObject.class);
    }
}
