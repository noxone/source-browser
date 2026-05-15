package com.hlag.sourceviewer.adapter.outgoing.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Parses a comma-separated file filter string into PostgreSQL-compatible regex patterns.
 *
 * <p>Supported syntax per token:
 * <ul>
 *   <li>{@code *.java} — glob pattern; {@code *} and {@code **} both match any character sequence
 *       including path separators; {@code ?} matches any single character except {@code /}</li>
 *   <li>{@code /regex/} — raw regular expression (between slashes)</li>
 *   <li>{@code !*.java} or {@code !/regex/} — negated form of either of the above</li>
 * </ul>
 *
 * <p>Multiple patterns are separated by commas. Include patterns are combined with {@code |} into
 * one OR regex; all exclude patterns are combined likewise and applied as {@code NOT}.
 */
class FileFilterParser {

    record ParsedFilter(String includeRegex, String excludeRegex) {
        boolean hasInclude() { return includeRegex != null; }
        boolean hasExclude() { return excludeRegex != null; }
        boolean isActive()   { return hasInclude() || hasExclude(); }
    }

    static ParsedFilter parse(String filterInput) {
        if (filterInput == null || filterInput.isBlank()) {
            return new ParsedFilter(null, null);
        }

        List<String> includes = new ArrayList<>();
        List<String> excludes = new ArrayList<>();

        for (String raw : filterInput.split(",")) {
            String token = raw.strip();
            if (token.isEmpty()) continue;

            boolean negated = token.startsWith("!");
            if (negated) token = token.substring(1).strip();

            String regex;
            if (token.startsWith("/") && token.endsWith("/") && token.length() > 2) {
                regex = token.substring(1, token.length() - 1);
            } else {
                regex = globToRegex(token);
            }

            if (negated) excludes.add(regex);
            else includes.add(regex);
        }

        String includeRegex = includes.isEmpty() ? null : combineAlternatives(includes);
        String excludeRegex = excludes.isEmpty() ? null : combineAlternatives(excludes);
        return new ParsedFilter(includeRegex, excludeRegex);
    }

    private static String combineAlternatives(List<String> patterns) {
        if (patterns.size() == 1) return patterns.get(0);
        return "(?:" + patterns.stream().collect(Collectors.joining("|")) + ")";
    }

    static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> {
                    // Both * and ** map to .* (crosses path separators by design)
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        i++;
                    }
                    sb.append(".*");
                }
                case '?' -> sb.append("[^/]");
                case '.', '(', ')', '[', ']', '{', '}', '+', '^', '$', '|', '\\' ->
                        sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        sb.append("$");
        return sb.toString();
    }
}
