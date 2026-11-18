package com.disasterrelief.backend.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A validated model suggestion. It is advisory only and never creates or changes a request by itself. */
record ReportAnalysis(String severity, String summary, List<Need> needs, List<String> uncertainties) {

    record Need(String resource, Integer quantity, String urgency) {
    }

    Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("severity", severity);
        map.put("summary", summary);
        List<Object> needList = new ArrayList<>();
        for (Need n : needs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("resource", n.resource());
            item.put("quantity", n.quantity());
            item.put("urgency", n.urgency());
            needList.add(item);
        }
        map.put("needs", needList);
        map.put("uncertainties", new ArrayList<Object>(uncertainties));
        return map;
    }
}
