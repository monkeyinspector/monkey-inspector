package io.github.monkeyinspector.web;

import java.util.*;

/** Small strict JSON codec for bounded API messages; no polymorphic deserialization. */
public final class Json {
    private Json() {}
    public static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String || value instanceof Enum<?>) {
            StringBuilder out = new StringBuilder("\"");
            for (char c : value.toString().toCharArray()) {
                if (c == '"' || c == '\\') out.append('\\').append(c);
                else if (c < 32) out.append(String.format("\\u%04x", (int)c));
                else out.append(c);
            }
            return out.append('"').toString();
        }
        if (value instanceof Number n) return Double.isFinite(n.doubleValue()) ? n.toString() : "null";
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            var items = new ArrayList<String>();
            map.forEach((k,v) -> items.add(stringify(k.toString()) + ":" + stringify(v)));
            return "{" + String.join(",", items) + "}";
        }
        if (value instanceof Collection<?> list) return "[" + String.join(",", list.stream().map(Json::stringify).toList()) + "]";
        throw new IllegalArgumentException("Unsupported JSON value");
    }
    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(String text) {
        Parser parser = new Parser(text);
        Object result = parser.value(0); parser.space();
        if (parser.i != text.length() || !(result instanceof Map)) throw new IllegalArgumentException("Expected JSON object");
        return (Map<String, Object>)result;
    }
    private static final class Parser {
        final String text; int i;
        Parser(String text) { this.text = text; }
        void space() { while (i < text.length() && " \r\n\t".indexOf(text.charAt(i)) >= 0) i++; }
        boolean take(char c) { space(); if (i < text.length() && text.charAt(i) == c) { i++; return true; } return false; }
        void need(char c) { if (!take(c)) throw new IllegalArgumentException("Malformed JSON"); }
        Object value(int depth) {
            if (depth > 16) throw new IllegalArgumentException("JSON nesting limit");
            space(); if (i >= text.length()) throw new IllegalArgumentException("Incomplete JSON");
            if (text.charAt(i) == '"') return string();
            if (take('{')) {
                Map<String,Object> result = new LinkedHashMap<>();
                if (take('}')) return result;
                do { String key = string(); need(':'); if (result.containsKey(key)) throw new IllegalArgumentException("Duplicate key"); result.put(key, value(depth + 1)); } while (take(','));
                need('}'); return result;
            }
            if (take('[')) {
                List<Object> result = new ArrayList<>();
                if (take(']')) return result;
                do { result.add(value(depth + 1)); } while (take(','));
                need(']'); return result;
            }
            for (String literal : List.of("true", "false", "null")) {
                if (text.startsWith(literal, i)) { i += literal.length(); return literal.equals("null") ? null : Boolean.valueOf(literal); }
            }
            var match = java.util.regex.Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?").matcher(text).region(i, text.length());
            if (!match.lookingAt()) throw new IllegalArgumentException("Invalid JSON value");
            i = match.end(); double number = Double.parseDouble(match.group());
            if (!Double.isFinite(number)) throw new IllegalArgumentException("Nonfinite number");
            return number;
        }
        String string() {
            need('"'); StringBuilder out = new StringBuilder();
            while (i < text.length()) {
                char c = text.charAt(i++);
                if (c == '"') return out.toString();
                if (c < 32) throw new IllegalArgumentException("Invalid string");
                if (c == '\\') {
                    if (i == text.length()) throw new IllegalArgumentException("Invalid escape");
                    char e = text.charAt(i++);
                    c = switch (e) {
                        case '"', '\\', '/' -> e;
                        case 'b' -> '\b'; case 'f' -> '\f'; case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                        case 'u' -> { if (i + 4 > text.length()) throw new IllegalArgumentException("Invalid Unicode escape"); int n = Integer.parseInt(text.substring(i, i + 4), 16); i += 4; yield (char)n; }
                        default -> throw new IllegalArgumentException("Invalid escape");
                    };
                }
                out.append(c);
            }
            throw new IllegalArgumentException("Unclosed string");
        }
    }
}
