package com.sftpmanager.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmptyStringDeserializerTest {

    static class Payload {
        public String value;
    }

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(String.class, new EmptyStringDeserializer());
        mapper.registerModule(module);
    }

    @Test
    void emptyJsonStringBecomesNull() throws Exception {
        Payload p = mapper.readValue("{\"value\":\"\"}", Payload.class);
        assertThat(p.value).isNull();
    }

    @Test
    void whitespaceOnlyJsonStringBecomesNull() throws Exception {
        Payload p = mapper.readValue("{\"value\":\"   \"}", Payload.class);
        assertThat(p.value).isNull();
    }

    @Test
    void jsonNullStaysNull() throws Exception {
        Payload p = mapper.readValue("{\"value\":null}", Payload.class);
        assertThat(p.value).isNull();
    }

    @Test
    void normalValueIsPreserved() throws Exception {
        Payload p = mapper.readValue("{\"value\":\"hello\"}", Payload.class);
        assertThat(p.value).isEqualTo("hello");
    }
}
