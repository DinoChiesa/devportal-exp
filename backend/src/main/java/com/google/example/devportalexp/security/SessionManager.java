// Copyright © 2025 Google LLC.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package com.google.example.devportalexp.security;

import com.google.example.devportalexp.model.Session;
import io.javalin.http.Context;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionManager {

  private static final Logger log = LoggerFactory.getLogger(SessionManager.class);
  private static final String SESSION_COOKIE_NAME = "devportalSessionId";
  private static final SecureRandom random = new SecureRandom();
  // Use ConcurrentHashMap for thread safety
  private final Map<String, Session> activeSessions = new ConcurrentHashMap<>();

  // TODO: Implement periodic cleanup of expired sessions

  public Session createSession(
      Context ctx,
      String userId,
      String email,
      String name,
      String firstName,
      String lastName,
      Instant jwtExpiry) {
    String sessionId = generateSessionId();
    Instant now = Instant.now();
    // Ensure session expiry doesn't exceed JWT expiry
    Instant sessionExpiry =
        jwtExpiry.isBefore(now)
            ? now.plusSeconds(60)
            : jwtExpiry; // Handle edge case of already expired JWT
    long maxAgeSeconds = Duration.between(now, sessionExpiry).getSeconds();

    if (maxAgeSeconds <= 0) {
      log.warn("JWT is expired. Cannot create session.");
      // Or throw an exception / handle differently
      return null;
    }

    Session session =
        new Session(sessionId, userId, email, name, firstName, lastName, sessionExpiry);
    activeSessions.put(sessionId, session);

    log.info(
        "Creating session {} for user {} {} ({}, {}), expires at {}, maxAge {}",
        sessionId,
        email,
        name,
        firstName,
        lastName,
        sessionExpiry,
        maxAgeSeconds);

    // Create and configure the session cookie
    io.javalin.http.Cookie sessionCookie =
        new io.javalin.http.Cookie(
            SESSION_COOKIE_NAME,
            sessionId,
            "/", // Path for the cookie
            (int) maxAgeSeconds, // Max age in seconds
            true, // Secure flag (send only over HTTPS) - Set to false for local HTTP testing if
            // needed
            0, // Cookie version (typically 0)
            true, // HttpOnly flag (prevent client-side script access)
            null // Optional: SameSite attribute (e.g., "Lax", "Strict", "None")
            );
    ctx.cookie(sessionCookie); // Set the configured cookie

    return session;
  }

  /** Retrieves the current session based on the request cookie. */
  public Optional<Session> getSession(Context ctx) {
    String sessionId = ctx.cookie(SESSION_COOKIE_NAME);
    if (sessionId == null) {
      return Optional.empty();
    }

    Session session = activeSessions.get(sessionId);
    if (session == null) {
      log.debug("Session ID {} found in cookie but not in active sessions.", sessionId);
      clearSessionCookie(ctx); // Clear invalid cookie
      return Optional.empty();
    }

    if (session.isExpired()) {
      log.info("Session {} for user {} has expired.", sessionId, session.email());
      activeSessions.remove(sessionId);
      clearSessionCookie(ctx); // Clear expired cookie
      return Optional.empty();
    }

    // Optional: Extend session expiry on activity? (Sliding window)

    return Optional.of(session);
  }

  /** Invalidates the session and clears the cookie. */
  public void invalidateSession(Context ctx) {
    String sessionId = ctx.cookie(SESSION_COOKIE_NAME);
    if (sessionId != null) {
      Session removed = activeSessions.remove(sessionId);
      if (removed != null) {
        log.info("Invalidated session {} for user {}", sessionId, removed.email());
      }
    }
    clearSessionCookie(ctx);
  }

  private void clearSessionCookie(Context ctx) {
    log.debug("Clearing session cookie {}", SESSION_COOKIE_NAME);
    ctx.removeCookie(SESSION_COOKIE_NAME, "/"); // Ensure path matches if set previously
  }

  private String generateSessionId() {
    byte[] randomBytes = new byte[32];
    random.nextBytes(randomBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }
}
