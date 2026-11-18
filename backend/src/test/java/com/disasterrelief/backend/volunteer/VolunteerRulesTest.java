package com.disasterrelief.backend.volunteer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VolunteerRulesTest {

    @Test
    void assignmentMovesOnlyForward() {
        assertTrue(VolunteerRules.canMove("ASSIGNED", "ACCEPTED"));
        assertTrue(VolunteerRules.canMove("ASSIGNED", "DECLINED"));
        assertTrue(VolunteerRules.canMove("ACCEPTED", "COMPLETED"));
        assertFalse(VolunteerRules.canMove("ASSIGNED", "COMPLETED"));
        assertFalse(VolunteerRules.canMove("COMPLETED", "ACCEPTED"));
        assertFalse(VolunteerRules.canMove("DECLINED", "ACCEPTED"));
        assertFalse(VolunteerRules.canMove("ACCEPTED", "DECLINED"));
    }

    @Test
    void onlyDeclinedAndCompletedFreeTheVolunteer() {
        assertTrue(VolunteerRules.freesVolunteer("DECLINED"));
        assertTrue(VolunteerRules.freesVolunteer("COMPLETED"));
        assertFalse(VolunteerRules.freesVolunteer("ACCEPTED"));
    }

    @Test
    void availabilityIsNormalizedAndChecked() {
        assertEquals("OFF_DUTY", VolunteerRules.normalizeAvailability(" off_duty "));
        assertNull(VolunteerRules.normalizeAvailability("sleeping"));
        assertNull(VolunteerRules.normalizeAvailability(null));
    }

    @Test
    void staffRoles() {
        assertTrue(VolunteerRules.isStaff("OFFICER"));
        assertTrue(VolunteerRules.isStaff("NGO"));
        assertFalse(VolunteerRules.isStaff("VOLUNTEER"));
        assertFalse(VolunteerRules.isStaff(null));
    }
}
