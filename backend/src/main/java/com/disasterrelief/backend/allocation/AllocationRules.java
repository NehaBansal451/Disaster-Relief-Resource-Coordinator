package com.disasterrelief.backend.allocation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Pure allocation logic (blueprint 7.2), no database. Documented policy:
 * 1) only unexpired stock with available quantity is eligible;
 * 2) nearest warehouse first, then earliest expiry (use stock that expires first), then lowest id;
 * 3) FAIRNESS: when other open requests (VERIFIED or APPROVED) want the same resource and the total demand exceeds the
 *    total available stock, this request is capped at its proportional share
 *    floor(totalAvailable x thisQty / (thisQty + otherDemand)), at least 1 unit, so one area cannot take all scarce stock;
 * 4) the plan shows the unfilled quantity and why each warehouse was or was not used.
 */
public final class AllocationRules {

    private AllocationRules() {
    }

    public record Candidate(long warehouseId, String warehouseName, int available, double distanceKm, LocalDate earliestExpiry) {
    }

    public record Pick(long warehouseId, String warehouseName, int quantity, double distanceKm, String reason) {
    }

    public record Skipped(long warehouseId, String reason) {
    }

    public record Plan(List<Pick> picks, List<Skipped> skipped, int allocated, int unfilled, String explanation) {
    }

    /** Share of scarce stock this request may take. Returns requested when stock covers all demand. */
    static int fairCap(int requested, int competingDemand, int totalAvailable) {
        if (competingDemand <= 0 || requested + competingDemand <= totalAvailable) {
            return requested;
        }
        int share = (int) Math.floor((double) totalAvailable * requested / (requested + competingDemand));
        return Math.min(requested, totalAvailable > 0 ? Math.max(1, share) : 0);
    }

    public static Plan recommend(int requested, int competingDemand, List<Candidate> candidates) {
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(Candidate::distanceKm)
                .thenComparing(Candidate::earliestExpiry, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingLong(Candidate::warehouseId));
        int totalAvailable = sorted.stream().mapToInt(Candidate::available).sum();
        int cap = fairCap(requested, competingDemand, totalAvailable);

        List<Pick> picks = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        int remaining = cap;
        int rank = 0;
        for (Candidate c : sorted) {
            if (remaining <= 0) {
                skipped.add(new Skipped(c.warehouseId(), "Not needed: the quantity is already covered by nearer warehouses."));
                continue;
            }
            int take = Math.min(remaining, c.available());
            rank++;
            picks.add(new Pick(c.warehouseId(), c.warehouseName(), take, c.distanceKm(), String.format(Locale.ROOT,
                    "%s warehouse by distance (%.1f km); %d available, %d allocated%s.",
                    rank == 1 ? "Nearest" : "Next nearest", c.distanceKm(), c.available(), take,
                    c.earliestExpiry() == null ? "" : "; earliest expiry " + c.earliestExpiry())));
            remaining -= take;
        }
        int allocated = cap - remaining;
        int unfilled = requested - allocated;

        StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "Allocated %d of %d requested.", allocated, requested));
        if (totalAvailable < requested) {
            text.append(" Total available stock in all warehouses is ").append(totalAvailable)
                    .append(", so the shortfall cannot be filled now.");
        }
        if (cap < requested && totalAvailable >= cap && competingDemand > 0 && requested + competingDemand > totalAvailable) {
            text.append(" Fairness policy: other open requests need ").append(competingDemand)
                    .append(" of the same resource, so this request is capped at its proportional share (").append(cap).append(").");
        }
        if (picks.isEmpty()) {
            text.append(" No warehouse has unexpired, unreserved stock of this resource.");
        }
        return new Plan(List.copyOf(picks), List.copyOf(skipped), allocated, unfilled, text.toString());
    }
}
