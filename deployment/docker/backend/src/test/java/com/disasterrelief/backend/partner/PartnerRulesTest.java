package com.disasterrelief.backend.partner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PartnerRulesTest {

    @Test
    void offerIsDecidedOnceThenReceived() {
        assertTrue(PartnerRules.canMove("OFFERED", "ACCEPTED"));
        assertTrue(PartnerRules.canMove("OFFERED", "DECLINED"));
        assertTrue(PartnerRules.canMove("ACCEPTED", "RECEIVED"));
        assertFalse(PartnerRules.canMove("OFFERED", "RECEIVED"));
        assertFalse(PartnerRules.canMove("DECLINED", "ACCEPTED"));
        assertFalse(PartnerRules.canMove("RECEIVED", "ACCEPTED"));
    }
}
