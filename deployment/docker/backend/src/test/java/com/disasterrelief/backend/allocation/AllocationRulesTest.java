package com.disasterrelief.backend.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disasterrelief.backend.allocation.AllocationRules.Candidate;
import com.disasterrelief.backend.allocation.AllocationRules.Plan;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AllocationRulesTest {

    private static Candidate c(long id, int available, double km, LocalDate expiry) {
        return new Candidate(id, "W" + id, available, km, expiry);
    }

    @Test
    void nearestWarehouseIsUsedFirstAndSplitIsFull() {
        Plan plan = AllocationRules.recommend(120, 0, List.of(c(2, 50, 12.0, null), c(1, 100, 5.0, null)));
        assertEquals(120, plan.allocated());
        assertEquals(0, plan.unfilled());
        assertEquals(1, plan.picks().get(0).warehouseId());
        assertEquals(100, plan.picks().get(0).quantity());
        assertEquals(20, plan.picks().get(1).quantity());
        assertTrue(plan.picks().get(0).reason().startsWith("Nearest"));
        assertTrue(plan.picks().get(1).reason().startsWith("Next nearest"));
    }

    @Test
    void shortageGivesPartialAllocationWithExplanation() {
        Plan plan = AllocationRules.recommend(200, 0, List.of(c(1, 100, 5.0, null), c(2, 50, 12.0, null)));
        assertEquals(150, plan.allocated());
        assertEquals(50, plan.unfilled());
        assertTrue(plan.explanation().contains("Total available stock in all warehouses is 150"));
    }

    @Test
    void warehouseNotNeededIsExplained() {
        Plan plan = AllocationRules.recommend(50, 0, List.of(c(1, 100, 5.0, null), c(2, 50, 12.0, null)));
        assertEquals(1, plan.picks().size());
        assertEquals(2, plan.skipped().get(0).warehouseId());
        assertTrue(plan.skipped().get(0).reason().startsWith("Not needed"));
    }

    @Test
    void earlierExpiryWinsWhenDistanceIsEqual() {
        Plan plan = AllocationRules.recommend(10, 0, List.of(
                c(1, 100, 5.0, LocalDate.of(2027, 6, 1)), c(2, 100, 5.0, LocalDate.of(2027, 1, 1))));
        assertEquals(2, plan.picks().get(0).warehouseId());
    }

    @Test
    void noStockGivesAnEmptyPlanNotAnError() {
        Plan plan = AllocationRules.recommend(10, 0, List.of());
        assertEquals(0, plan.allocated());
        assertEquals(10, plan.unfilled());
        assertTrue(plan.picks().isEmpty());
        assertTrue(plan.explanation().contains("No warehouse has"));
    }

    @Test
    void fairnessCapsARequestWhenDemandExceedsStock() {
        assertEquals(5, AllocationRules.fairCap(8, 8, 10));
        assertEquals(8, AllocationRules.fairCap(8, 0, 10));
        assertEquals(8, AllocationRules.fairCap(8, 2, 10));
        assertEquals(1, AllocationRules.fairCap(1, 1, 1));
        assertEquals(0, AllocationRules.fairCap(5, 5, 0));
        Plan plan = AllocationRules.recommend(100, 100, List.of(c(1, 100, 5.0, null)));
        assertEquals(50, plan.allocated());
        assertTrue(plan.explanation().contains("Fairness policy"));
    }

    @Test
    void neverAllocatesMoreThanRequestedOrAvailable() {
        Plan plan = AllocationRules.recommend(30, 0, List.of(c(1, 500, 1.0, null)));
        assertEquals(30, plan.allocated());
        assertEquals(30, plan.picks().get(0).quantity());
    }
}
