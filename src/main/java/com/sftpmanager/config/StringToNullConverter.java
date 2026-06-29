package com.sftpmanager.config;

import com.fasterxml.jackson.databind.util.StdConverter;

public class StringToNullConverter extends StdConverter<String, String> {
    @Override
    public String convert(String value) {
        return (value != null && value.trim().isEmpty()) ? null : value;
    }
}
