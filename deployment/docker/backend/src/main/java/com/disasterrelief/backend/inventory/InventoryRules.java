package com.disasterrelief.backend.inventory;

import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.common.FieldProblem;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Pure stock rules (no database), so they are unit-testable. */
final class InventoryRules {

    static final int EXPIRING_SOON_DAYS = 30;
    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private InventoryRules() {
    }

    static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** Available stock. Reserved stock is still on hand, so it is subtracted once and never counted twice. */
    static int available(int onHand, int reserved) {
        return onHand - reserved;
    }

    /** No batch given means "unbatched stock" (stored as NULL); the database allows one such row per warehouse/resource. */
    static String normalizeBatch(String batchRef) {
        return batchRef == null || batchRef.isBlank() ? null : batchRef.trim().toUpperCase();
    }

    static String expiryStatus(LocalDate expiry, LocalDate today) {
        if (expiry == null) {
            return "NONE";
        }
        if (expiry.isBefore(today)) {
            return "EXPIRED";
        }
        return expiry.isAfter(today.plusDays(EXPIRING_SOON_DAYS)) ? "OK" : "EXPIRING_SOON";
    }

    /** A receipt of a perishable resource must carry a future expiry date; stock that is already expired is refused. */
    static void validateReceipt(boolean perishable, LocalDate expiry, LocalDate today) {
        List<FieldProblem> errors = new ArrayList<>();
        if (perishable && expiry == null) {
            errors.add(new FieldProblem("expiryDate", "expiryDate is required for a perishable resource."));
        }
        if (expiry != null && !expiry.isAfter(today)) {
            errors.add(new FieldProblem("expiryDate", "expiryDate must be in the future."));
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
    }
}
