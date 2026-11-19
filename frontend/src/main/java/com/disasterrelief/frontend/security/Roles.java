package com.disasterrelief.frontend.security;

import java.util.Arrays;

/** Which role sees which screen. UI convenience only: the backend enforces the real permission on every call. */
public final class Roles {

    public static final String[] STAFF = {"ADMIN", "OFFICER", "WAREHOUSE_MANAGER", "FIELD_WORKER", "NGO"};
    public static final String[] DECIDERS = {"ADMIN", "OFFICER"};
    public static final String[] LOGISTICS = {"ADMIN", "OFFICER", "WAREHOUSE_MANAGER"};
    public static final String[] VOLUNTEER_DESK = {"ADMIN", "OFFICER", "NGO"};
    public static final String[] PARTNER_DESK = {"ADMIN", "OFFICER", "NGO"};

    private Roles() {
    }

    public static boolean in(String role, String... allowed) {
        return role != null && Arrays.asList(allowed).contains(role);
    }
}
