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

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.net.MalformedURLException;
import java.net.URI;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtValidator {
  private static final Logger log = LoggerFactory.getLogger(JwtValidator.class);
  private static final String JWKS_URL =
      "https://www.googleapis.com/robot/v1/metadata/jwk/securetoken@system.gserviceaccount.com";
  private static final String ISSUER_PREFIX = "https://securetoken.google.com/";

  private final JwkProvider jwkProvider;
  private final String expectedIssuer;
  private final String expectedAudience;

  public JwtValidator(Map<String, Object> appSettings) {
    try {
      final String firebaseProject = (String) appSettings.get("project");
      this.jwkProvider = new UrlJwkProvider(URI.create(JWKS_URL).toURL());
      this.expectedIssuer = ISSUER_PREFIX + firebaseProject;
      this.expectedAudience = firebaseProject;
    } catch (MalformedURLException | IllegalArgumentException e) {
      log.error("Invalid JWKS URL: {}", JWKS_URL, e);
      throw new RuntimeException("Failed to initialize JwkProvider due to invalid URL", e);
    }
  }

  /**
   * Validates the Firebase ID token. Checks signature, issuer, audience, and expiry.
   *
   * @param token The Firebase ID token string.
   * @return The decoded JWT if valid, otherwise null.
   */
  public DecodedJWT validateToken(String token) {
    try {
      // Decode without verification first to get kid
      DecodedJWT jwt = JWT.decode(token);

      String keyId = jwt.getKeyId();
      if (keyId == null) {
        log.warn("ID token is missing 'kid' (Key ID) in header.");
        return null;
      }

      // Fetch the public key from Google's JWKS endpoint
      Jwk jwk = jwkProvider.get(keyId);
      if (!(jwk.getPublicKey() instanceof RSAPublicKey)) {
        log.error("Invalid key type fetched from JWKS. Expected RSAPublicKey.");
        return null;
      }
      RSAPublicKey publicKey = (RSAPublicKey) jwk.getPublicKey();

      // Configure the verifier
      Algorithm algorithm = Algorithm.RSA256(publicKey, null); // Use RSA256 with the public key
      com.auth0.jwt.interfaces.JWTVerifier verifier =
          JWT.require(algorithm)
              .withIssuer(expectedIssuer)
              .withAudience(expectedAudience)
              // Allow for some clock skew (e.g., 60 seconds)
              .acceptLeeway(30)
              .build();

      // Verify the token
      DecodedJWT verifiedJwt = verifier.verify(token);
      log.info("Successfully verified ID token for user: {}", verifiedJwt.getSubject());
      return verifiedJwt;

    } catch (JwkException e) {
      log.warn(
          "Failed to fetch public key (kid: {}) from JWKS: {}",
          JWT.decode(token).getKeyId(),
          e.getMessage());
      return null;
    } catch (JWTVerificationException e) {
      // This catches signature errors, expired tokens, issuer/audience mismatch, etc.
      log.warn("ID token verification failed: {}", e.getMessage());
      return null;
    } catch (Exception e) {
      // Catch unexpected errors during decoding or verification
      log.error("Unexpected error during token validation", e);
      return null;
    }
  }
}
