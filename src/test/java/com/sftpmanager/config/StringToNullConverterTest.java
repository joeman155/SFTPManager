package com.sftpmanager.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StringToNullConverterTest {

    private final StringToNullConverter converter = new StringToNullConverter();

    @Test
    void emptyStringBecomesNull() {
        assertThat(converter.convert("")).isNull();
    }

    @Test
    void whitespaceOnlyStringBecomesNull() {
        assertThat(converter.convert("   \t ")).isNull();
    }

    @Test
    void nullStaysNull() {
        assertThat(converter.convert(null)).isNull();
    }

    @Test
    void normalValueIsPreservedUntrimmed() {
        assertThat(converter.convert(" hello ")).isEqualTo(" hello ");
    }
}
