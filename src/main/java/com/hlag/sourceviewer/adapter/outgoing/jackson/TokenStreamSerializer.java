package com.hlag.sourceviewer.adapter.outgoing.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.hlag.sourceviewer.domain.model.source.ExtractedToken;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Serializes a list of {@link ExtractedToken} objects to gzip-compressed JSON bytes.
 * Uses compact single-character field names to minimize payload size before compression.
 */
@ApplicationScoped
public class TokenStreamSerializer {

    private final ObjectMapper mapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public byte[] serialize(List<ExtractedToken> tokens) {
        try {
            ArrayNode array = mapper.createArrayNode();
            for (ExtractedToken t : tokens) {
                var node = mapper.createObjectNode();
                node.put("l",  t.line());
                node.put("cs", t.columnStart());
                node.put("ce", t.columnEnd());
                node.put("t",  t.text());
                node.put("k",  t.kind().name());
                if (t.qualifiedName() != null) node.put("q", t.qualifiedName());
                if (t.symbolId()      != null) node.put("s", t.symbolId());
                if (t.hoverText()     != null) node.put("h", t.hoverText());
                array.add(node);
            }
            byte[] json = mapper.writeValueAsBytes(array);
            var baos = new ByteArrayOutputStream(json.length / 2);
            try (var gzip = new GZIPOutputStream(baos)) {
                gzip.write(json);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize token stream", e);
        }
    }
}
