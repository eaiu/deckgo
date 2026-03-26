package com.deckgo.backend.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Iterator;
import java.util.Map;

public final class JsonSanitizer {

    private JsonSanitizer() {}

    /**
     * Strip \u0000 (null byte) from all text values in a JsonNode tree.
     * PostgreSQL jsonb does not support \u0000.
     */
    public static JsonNode sanitize(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return node;
        }
        if (node.isTextual()) {
            String text = node.asText();
            if (text.indexOf('\u0000') >= 0) {
                return new TextNode(text.replace("\u0000", ""));
            }
            return node;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode sanitized = sanitize(entry.getValue());
                if (sanitized != entry.getValue()) {
                    obj.set(entry.getKey(), sanitized);
                }
            }
            return obj;
        }
        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                JsonNode sanitized = sanitize(arr.get(i));
                if (sanitized != arr.get(i)) {
                    arr.set(i, sanitized);
                }
            }
            return arr;
        }
        return node;
    }
}
