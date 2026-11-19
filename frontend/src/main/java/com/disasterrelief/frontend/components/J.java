package com.disasterrelief.frontend.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Safe readers for the plain maps the backend returns, plus a tiny JSON writer for the map script. */
public final class J {

    private J() {
    }

    /** Text value, or "" when absent. */
    public static String str(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v == null ? "" : v.toString();
    }

    public static long lng(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v instanceof Number n ? n.longValue() : 0L;
    }

    public static double dbl(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    public static boolean bool(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v instanceof Boolean b && b;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> list(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        if (v instanceof List<?> raw) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : raw) {
                if (item instanceof Map<?, ?> row) {
                    out.add((Map<String, Object>) row);
                }
            }
            return out;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v instanceof Map<?, ?> row ? (Map<String, Object>) row : Map.of();
    }

    /** Plain strings in a list (for example the analyzer's "uncertainties"). */
    public static List<String> strings(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> raw) {
            for (Object item : raw) {
                out.add(String.valueOf(item));
            }
        }
        return out;
    }

    /** Map/List/String/Number/Boolean/null to JSON text. */
    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                quote(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                write(sb, list.get(i));
            }
            sb.append(']');
        } else {
            quote(sb, value.toString());
        }
    }

    private static void quote(StringBuilder sb, String text) {
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<' -> sb.append("\\u003c");
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
