package com.disasterrelief.backend.volunteer;

import java.util.Set;

/** Pure volunteer rules (no database). */
final class VolunteerRules {

    static final Set<String> AVAILABILITY = Set.of("AVAILABLE", "BUSY", "OFF_DUTY");
    static final Set<String> STAFF_ROLES = Set.of("ADMIN", "OFFICER", "NGO");

    private VolunteerRules() {
    }

    /** Assignment life cycle: ASSIGNED -> ACCEPTED or DECLINED; ACCEPTED -> COMPLETED. Everything else is refused. */
    static boolean canMove(String from, String to) {
        return ("ASSIGNED".equals(from) && ("ACCEPTED".equals(to) || "DECLINED".equals(to)))
                || ("ACCEPTED".equals(from) && "COMPLETED".equals(to));
    }

    /** DECLINED and COMPLETED free the volunteer for new work. */
    static boolean freesVolunteer(String to) {
        return "DECLINED".equals(to) || "COMPLETED".equals(to);
    }

    static boolean isStaff(String role) {
        return role != null && STAFF_ROLES.contains(role);
    }

    static String normalizeAvailability(String value) {
        String v = value == null ? "" : value.trim().toUpperCase();
        return AVAILABILITY.contains(v) ? v : null;
    }
}
