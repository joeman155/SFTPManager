package com.sftpmanager.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import java.io.IOException;

public class EmptyStringDeserializer extends StringDeserializer {

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        String value = super.deserialize(p, ctx);
        return (value != null && value.trim().isEmpty()) ? null : value;
    }
}
