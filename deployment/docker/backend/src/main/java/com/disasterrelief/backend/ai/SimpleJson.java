package com.disasterrelief.backend.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small strict JSON reader/writer used only for model output, so the analyzer does not depend on a particular
 * Jackson version. Objects become LinkedHashMap, arrays ArrayList, numbers Double, plus String, Boolean and null.
 * Nesting deeper than 20 levels and trailing text are refused.
 */
final class SimpleJson {

    private static final int MAX_DEPTH = 20;

    private final String s;
    private int pos;

    private SimpleJson(String s) {
        this.s = s;
    }

    static Object parse(String text) {
        SimpleJson p = new SimpleJson(text);
        p.skip();
        Object value = p.value(0);
        p.skip();
        if (p.pos != text.length()) {
            throw p.error("unexpected text after the JSON value");
        }
        return value;
    }

    private Object value(int depth) {
        if (depth > MAX_DEPTH) {
            throw error("JSON is nested too deeply");
        }
        if (pos >= s.length()) {
            throw error("unexpected end of JSON");
        }
        char c = s.charAt(pos);
        if (c == '{') {
            return object(depth);
        }
        if (c == '[') {
            return array(depth);
        }
        if (c == '"') {
            return string();
        }
        if (s.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (s.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        if (s.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        return number();
    }

    private Map<String, Object> object(int depth) {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        skip();
        if (peek('}')) {
            pos++;
            return map;
        }
        while (true) {
            skip();
            if (!peek('"')) {
                throw error("object key must be a string");
            }
            String key = string();
            skip();
            expect(':');
            skip();
            map.put(key, value(depth + 1));
            skip();
            if (peek(',')) {
                pos++;
                continue;
            }
            expect('}');
            return map;
        }
    }

    private List<Object> array(int depth) {
        List<Object> list = new ArrayList<>();
        pos++;
        skip();
        if (peek(']')) {
            pos++;
            return list;
        }
        while (true) {
            skip();
            list.add(value(depth + 1));
            skip();
            if (peek(',')) {
                pos++;
                continue;
            }
            expect(']');
            return list;
        }
    }

    private String string() {
        StringBuilder out = new StringBuilder();
        pos++;
        while (true) {
            if (pos >= s.length()) {
                throw error("unterminated string");
            }
            char c = s.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c < 0x20) {
                throw error("control character in string");
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (pos >= s.length()) {
                throw error("unterminated escape");
            }
            char e = s.charAt(pos++);
            switch (e) {
                case '"', '\\', '/' -> out.append(e);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (pos + 4 > s.length()) {
                        throw error("bad unicode escape");
                    }
                    try {
                        out.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("bad unicode escape");
                    }
                    pos += 4;
                }
                default -> throw error("bad escape");
            }
        }
    }

    private Double number() {
        int start = pos;
        while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) {
            pos++;
        }
        if (start == pos) {
            throw error("unexpected character");
        }
        try {
            return Double.valueOf(s.substring(start, pos));
        } catch (NumberFormatException ex) {
            throw error("bad number");
        }
    }

    private void skip() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
            pos++;
        }
    }

    private boolean peek(char c) {
        return pos < s.length() && s.charAt(pos) == c;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw error("expected '" + c + "'");
        }
        pos++;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("Invalid JSON at position " + pos + ": " + message);
    }

    // ---- writer

    static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb);
        return sb.toString();
    }

    private static void write(Object v, StringBuilder sb) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String str) {
            quote(str, sb);
        } else if (v instanceof Boolean || v instanceof Integer || v instanceof Long) {
            sb.append(v);
        } else if (v instanceof Double d) {
            sb.append(d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf(d.longValue()) : d.toString());
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                quote(String.valueOf(e.getKey()), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else if (v instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                write(list.get(i), sb);
            }
            sb.append(']');
        } else {
            throw new IllegalArgumentException("Unsupported JSON value: " + v.getClass().getName());
        }
    }

    private static void quote(String str, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
