package com.ayden.cinemabookingsystem.user;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name",
            nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false,
            length = 255, unique = true)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.CUSTOMER;

    @Column(name = "created_at", nullable = false,
            insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected AppUser() {
    }

    // Legacy constructor used by existing tests.
    // Users created this way cannot log in.
    public AppUser(String displayName, String email) {
        this.displayName = displayName;
        this.email = email;
        this.role = UserRole.CUSTOMER;
    }

    // Constructor for new registered users.
    public AppUser(
            String displayName,
            String email,
            String passwordHash
    ) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException(
                    "Password hash is required"
            );
        }

        this.displayName = displayName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = UserRole.CUSTOMER;
    }

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
