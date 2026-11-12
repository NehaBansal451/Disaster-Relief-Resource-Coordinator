package com.disasterrelief.backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Read-only view of the {@code users} table for authentication. Only the columns needed for login are mapped;
 * the entity is never returned from the API (see {@link UserSummary}). User management arrives in a later milestone.
 */
@Entity
@Table(name = "users")
public class AppUser {

    @Id
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "preferred_language", nullable = false)
    private String preferredLanguage;

    protected AppUser() {
        // required by JPA
    }

    public AppUser(Long id, String fullName, String email, String passwordHash, Role role, boolean enabled,
                   String preferredLanguage) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
        this.preferredLanguage = preferredLanguage;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }
}
