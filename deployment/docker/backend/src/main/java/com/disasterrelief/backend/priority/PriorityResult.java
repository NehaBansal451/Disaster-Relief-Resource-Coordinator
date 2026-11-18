package com.disasterrelief.backend.priority;

import java.math.BigDecimal;
import java.util.List;

public record PriorityResult(BigDecimal score, String category, List<String> reasons) {
}
