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

package com.google.example.devportalexp.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.example.devportalexp.security.JwtValidator;
import com.google.example.devportalexp.security.SessionManager;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthController {

  private static final Logger log = LoggerFactory.getLogger(AuthController.class);
  private final JwtValidator jwtValidator;
  private final SessionManager sessionManager;

  public AuthController(JwtValidator jwtValidator, SessionManager sessionManager) {
    this.jwtValidator = jwtValidator;
    this.sessionManager = sessionManager;
  }

  /** Handles POST /api/auth/login */
  public void login(Context ctx) {
    LoginRequest loginRequest = ctx.bodyAsClass(LoginRequest.class);
    String idToken = loginRequest.idToken();

    if (idToken == null || idToken.isBlank()) {
      log.warn("Login attempt with empty ID token.");
      ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "ID token is required."));
      return;
    }

    DecodedJWT validatedToken = jwtValidator.validateToken(idToken);

    if (validatedToken == null) {
      log.warn("Login attempt with invalid ID token.");
      ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "Invalid ID token."));
      return;
    }

    // Extract user info from validated token
    String userId = validatedToken.getSubject(); // 'sub' claim
    String email = validatedToken.getClaim("email").asString();

    // Firebase uses "name" but not necessarily given_ or family_name.
    String firstName = null;
    String lastName = null;
    String name = validatedToken.getClaim("name").asString();
    if (name == null) name = "Unknown User";
    List<String> parts =
        Arrays.stream(name.split("\\s+")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    if (parts.size() >= 2) {
      firstName = parts.get(0);
      lastName = parts.get(1);
    }
    // Provide defaults if names are missing (adjust as needed)
    if (email == null) email = "N/A";
    if (firstName == null) firstName = "Unknown";
    if (lastName == null) lastName = "User";

    // Create session and set cookie
    var session =
        sessionManager.createSession(
            ctx, userId, email, name, firstName, lastName, validatedToken.getExpiresAtAsInstant());

    if (session == null) {
      // Handle case where session couldn't be created (e.g., token already expired)
      ctx.status(HttpStatus.UNAUTHORIZED)
          .json(Map.of("error", "Could not create session, token might be expired."));
      return;
    }

    log.info("User '{}' successfully logged in. Session ID: {}", email, session.sessionId());
    ctx.status(HttpStatus.OK).json(Map.of("message", "Login successful", "email", email));
  }

  /** Handles POST /api/auth/logout */
  public void logout(Context ctx) {
    sessionManager.invalidateSession(ctx);
    log.info("User logged out.");
    ctx.status(HttpStatus.NO_CONTENT);
  }

  // Simple static inner class for JSON mapping
  public static class LoginRequest {
    private String idToken;

    public String idToken() {
      return idToken;
    }

    public void setIdToken(String idToken) {
      this.idToken = idToken;
    }
  }
}
