package com.wushi.module.spider.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonpParser {

    private final ObjectMapper objectMapper;

    public JsonpParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        String trimmed = body.trim();
        int start = trimmed.indexOf('(');
        int end = trimmed.lastIndexOf(')');
        try {
            if (start >= 0 && end > start) {
                return objectMapper.readTree(trimmed.substring(start + 1, end));
            }
            return objectMapper.readTree(trimmed);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("非法 JSON 内容: " + trimmed, e);
        }
    }

    public String strip(String body) {
        if (body == null || body.isBlank()) {
            return "{}";
        }
        String trimmed = body.trim();
        int start = trimmed.indexOf('(');
        int end = trimmed.lastIndexOf(')');
        if (start >= 0 && end > start) {
            return trimmed.substring(start + 1, end);
        }
        return trimmed;
    }
}
