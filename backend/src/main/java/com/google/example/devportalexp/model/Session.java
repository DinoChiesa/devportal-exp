package com.google.example.devportalexp.model;

import java.time.Instant;

/**
 * Represents an active user session.
 *
 * @param sessionId The unique ID for this session.
 * @param userId The Firebase user ID.
 * @param email The user's email.
 * @param email The user's name.
 * @param firstName The user's first name.
 * @param lastName The user's last name.
 * @param expiresAt The timestamp when this session expires (should match JWT expiry).
 */
public record Session(
    String sessionId,
    String userId,
    String email,
    String name,
    String firstName,
    String lastName,
    Instant expiresAt) {

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }
}
