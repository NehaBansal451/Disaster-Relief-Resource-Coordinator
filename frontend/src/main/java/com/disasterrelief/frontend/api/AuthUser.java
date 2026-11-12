package com.disasterrelief.frontend.api;

/** Logged-in user as returned by the backend ({id, fullName, email, role}). */
public record AuthUser(Long id, String fullName, String email, String role) {

    /** UI convenience only (which buttons to show). The backend enforces the real permission on every call. */
    public boolean canManageDisasters() {
        return "ADMIN".equals(role) || "OFFICER".equals(role);
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
