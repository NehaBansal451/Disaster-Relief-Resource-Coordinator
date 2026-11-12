package com.disasterrelief.backend.auth;

/** Public view of a user (contract: {id, fullName, email, role}). Never contains the password hash. */
public record UserSummary(Long id, String fullName, String email, Role role) {

    public static UserSummary from(AppUser user) {
        return new UserSummary(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
    }
}
