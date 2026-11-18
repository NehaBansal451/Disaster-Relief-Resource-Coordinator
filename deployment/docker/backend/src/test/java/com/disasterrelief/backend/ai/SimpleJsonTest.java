package com.disasterrelief.backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimpleJsonTest {

    @Test
    void parsesNestedObjectsArraysAndScalars() {
        Object v = SimpleJson.parse("{\"a\":1,\"b\":[true,false,null,\"x\"],\"c\":{\"d\":-2.5e1}}");
        Map<?, ?> m = (Map<?, ?>) v;
        assertEquals(1.0, m.get("a"));
        List<?> b = (List<?>) m.get("b");
        assertEquals(Boolean.TRUE, b.get(0));
        assertNull(b.get(2));
        assertEquals("x", b.get(3));
        assertEquals(-25.0, ((Map<?, ?>) m.get("c")).get("d"));
    }

    @Test
    void parsesEscapes() {
        assertEquals("a\"b\\c\ndé", SimpleJson.parse("\"a\\\"b\\\\c\\nd\\u00e9\""));
    }

    @Test
    void refusesBrokenOrTrailingJson() {
        assertThrows(IllegalArgumentException.class, () -> SimpleJson.parse("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> SimpleJson.parse("{\"a\":1} extra"));
        assertThrows(IllegalArgumentException.class, () -> SimpleJson.parse("{\"a\":\"unterminated}"));
        assertThrows(IllegalArgumentException.class, () -> SimpleJson.parse("[1,2"));
        assertThrows(IllegalArgumentException.class, () -> SimpleJson.parse(""));
    }

    @Test
    void refusesVeryDeepNesting() {
        String deep = "[".repeat(40) + "]".repeat(40);
        assertThrows(IllegalArgumentException.class, () -> SimpleJson.parse(deep));
    }

    @Test
    void writesAndReadsBack() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("s", "line\n\"q\"");
        m.put("n", 5);
        m.put("d", 1.5);
        m.put("z", null);
        m.put("l", List.of("a", true));
        String json = SimpleJson.write(m);
        assertEquals("{\"s\":\"line\\n\\\"q\\\"\",\"n\":5,\"d\":1.5,\"z\":null,\"l\":[\"a\",true]}", json);
        Map<?, ?> back = (Map<?, ?>) SimpleJson.parse(json);
        assertEquals("line\n\"q\"", back.get("s"));
        assertTrue(back.containsKey("z"));
    }
}
