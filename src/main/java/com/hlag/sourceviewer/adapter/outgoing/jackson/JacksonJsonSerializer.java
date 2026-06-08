package com.hlag.sourceviewer.adapter.outgoing.jackson;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hlag.sourceviewer.domain.port.outgoing.JsonSerializer;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

@ApplicationScoped
public class JacksonJsonSerializer implements JsonSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Override
    public Map<String, Object> parseToMap(String json) {
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }

    @Override
    public String serialize(Object object) {
        try {
            return MAPPER.writeValueAsString(object);
        } catch (Exception e) {
            return "{}";
        }
    }
}
