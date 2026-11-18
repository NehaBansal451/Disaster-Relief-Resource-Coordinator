package com.disasterrelief.backend.partner;

/** Pure partner-contribution rules. OFFERED -> ACCEPTED or DECLINED; ACCEPTED -> RECEIVED. */
final class PartnerRules {

    private PartnerRules() {
    }

    static boolean canMove(String from, String to) {
        return ("OFFERED".equals(from) && ("ACCEPTED".equals(to) || "DECLINED".equals(to)))
                || ("ACCEPTED".equals(from) && "RECEIVED".equals(to));
    }
}
