package com.hlag.sourceviewer.application.scan.indexer;

import com.hlag.sourceviewer.domain.model.source.detail.AnnotationDetail;
import com.hlag.sourceviewer.domain.model.source.detail.MethodDetail;
import com.hlag.sourceviewer.domain.model.source.detail.TokenDetailType;
import com.hlag.sourceviewer.domain.model.source.detail.TypeRefDetail;
import com.hlag.sourceviewer.domain.model.source.detail.VariableDetail;
import com.hlag.sourceviewer.domain.port.outgoing.JsonSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses JDTLS hover text (Java-language code blocks) into structured token detail objects.
 *
 * <p>JDTLS hover returns a code block with language="java" containing a concise
 * signature or type description. This class maps those strings to typed detail records.</p>
 */
class HoverTextParser {

    // @interface FQN — annotation type
    private static final Pattern ANNOTATION_TYPE =
            Pattern.compile("^@interface\\s+(\\S+)");

    // Optional modifiers + (class|interface|enum|record) + FQN
    private static final Pattern TYPE_KEYWORD =
            Pattern.compile("^(?:[\\w ]+\\s+)?(class|interface|enum|record)\\s+(\\S+)");

    // ReturnType DeclaringClass.MethodName(params) — method with declaring class.
    // Uses lazy (\\S.*?) for return type to handle generics with spaces like "Class<? extends Foo>[]".
    // Declaring class uses only word-chars+dots (no spaces) so the lazy match stops at the right boundary.
    private static final Pattern METHOD_WITH_CLASS =
            Pattern.compile("^(\\S.*?)\\s+((?:[\\w$]+\\.)+)([\\w$]+)\\(([^)]*)\\)");

    // ReturnType MethodName(params) — method without declaring class
    private static final Pattern METHOD_NO_CLASS =
            Pattern.compile("^(\\S.*?)\\s+([\\w$]+)\\(([^)]*)\\)");

    // Optional modifiers + Type + Class.FieldName — field with class qualifier
    private static final Pattern FIELD_WITH_CLASS =
            Pattern.compile("^(?:[\\w ]+?\\s+)?(\\S+)\\s+([\\w$]+(?:\\.[\\w$]+)+)\\.(\\w+)$");

    // Type + name — local variable or parameter
    private static final Pattern SIMPLE_VARIABLE =
            Pattern.compile("^(?:[\\w@$.]+\\s+)*(\\S+?)\\s+(\\w+)$");

    record ParsedDetail(TokenDetailType type, Object detail) {}

    /** Returns null if the hover text cannot be parsed into a useful detail. */
    static ParsedDetail parse(String javaHoverCode) {
        if (javaHoverCode == null || javaHoverCode.isBlank()) {
            return null;
        }
        String code = javaHoverCode.strip();

        // @interface → annotation
        Matcher m = ANNOTATION_TYPE.matcher(code);
        if (m.find()) {
            return new ParsedDetail(TokenDetailType.ANNOTATION, new AnnotationDetail(m.group(1)));
        }

        // class/interface/enum/record → type reference
        m = TYPE_KEYWORD.matcher(code);
        if (m.find()) {
            String kw = m.group(1);
            String fqn = m.group(2).replaceAll("<.*", ""); // strip generics
            String kind = switch (kw) {
                case "interface" -> "INTERFACE";
                case "enum"      -> "ENUM";
                case "record"    -> "RECORD";
                default          -> "CLASS";
            };
            return new ParsedDetail(TokenDetailType.TYPE_REF, new TypeRefDetail(fqn, kind));
        }

        // Method with declaring class
        m = METHOD_WITH_CLASS.matcher(code);
        if (m.find()) {
            String returnType = m.group(1);
            String declaringClass = m.group(2).replaceAll("\\.$", "");
            String methodName = m.group(3);
            List<MethodDetail.MethodParam> params = parseParams(m.group(4));
            return new ParsedDetail(TokenDetailType.METHOD_CALL,
                    new MethodDetail(methodName, declaringClass, returnType, params, false));
        }

        // Field with class qualifier
        m = FIELD_WITH_CLASS.matcher(code);
        if (m.find()) {
            String typeName = m.group(1);
            String fieldName = m.group(3);
            return new ParsedDetail(TokenDetailType.VARIABLE,
                    new VariableDetail(fieldName, "FIELD", typeName));
        }

        // Method without declaring class (constructors, local methods)
        m = METHOD_NO_CLASS.matcher(code);
        if (m.find()) {
            String returnType = m.group(1);
            String methodName = m.group(2);
            // Avoid false positive: "String bla" would match but isn't a method
            // A method must be followed by `(` in the original string
            if (code.contains(methodName + "(")) {
                List<MethodDetail.MethodParam> params = parseParams(m.group(3));
                return new ParsedDetail(TokenDetailType.METHOD_CALL,
                        new MethodDetail(methodName, null, returnType, params, false));
            }
        }

        // Simple variable: Type name
        m = SIMPLE_VARIABLE.matcher(code);
        if (m.find() && !code.contains("(")) {
            String typeName = m.group(1);
            String varName = m.group(2);
            // Skip single-word hover (e.g. bare package names)
            if (typeName.isEmpty() || varName.isEmpty() || typeName.equals(varName)) {
                return null;
            }
            return new ParsedDetail(TokenDetailType.VARIABLE,
                    new VariableDetail(varName, "LOCAL", typeName));
        }

        return null;
    }

    private static List<MethodDetail.MethodParam> parseParams(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<MethodDetail.MethodParam> result = new ArrayList<>();
        // Split on commas not inside angle brackets
        int depth = 0;
        int start = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ',' && depth == 0) {
                parseParam(raw.substring(start, i).strip(), result);
                start = i + 1;
            }
        }
        parseParam(raw.substring(start).strip(), result);
        return result;
    }

    private static void parseParam(String token, List<MethodDetail.MethodParam> out) {
        if (token.isBlank()) return;
        // "TypeName paramName" or just "TypeName"
        int lastSpace = token.lastIndexOf(' ');
        if (lastSpace > 0) {
            String type = token.substring(0, lastSpace).strip();
            String name = token.substring(lastSpace + 1).strip();
            out.add(new MethodDetail.MethodParam(name, type));
        } else {
            out.add(new MethodDetail.MethodParam("", token));
        }
    }

    /** Serializes a detail POJO to a JSON string for storage in token_detail.detail. */
    static String toJson(Object detail, JsonSerializer jsonMapper) {
        return jsonMapper.serialize(detail);
    }
}
