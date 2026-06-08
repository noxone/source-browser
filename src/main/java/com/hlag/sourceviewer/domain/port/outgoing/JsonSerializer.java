package com.hlag.sourceviewer.domain.port.outgoing;

import java.util.Map;

public interface JsonSerializer {
    Map<String, Object> parseToMap(String json);
    String serialize(Object object);
}
