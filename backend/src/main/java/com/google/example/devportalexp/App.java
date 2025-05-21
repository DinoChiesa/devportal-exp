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

package com.google.example.devportalexp;

import static io.javalin.apibuilder.ApiBuilder.*;

import com.google.example.devportalexp.controller.ApigeeController;
import com.google.example.devportalexp.controller.AuthController;
import com.google.example.devportalexp.security.JwtValidator;
import com.google.example.devportalexp.security.SessionManager;
import com.google.example.devportalexp.service.StateService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UnauthorizedResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Supplier;

public class App {

  private Gson gson = new GsonBuilder().setPrettyPrinting().create();

  /**
   * Gets the port number from the PORT environment variable. Defaults to 7070 if the variable is
   * not set or cannot be parsed.
   *
   * @return The port number to use.
   */
  private static int getPort() {
    String portEnv = System.getenv("PORT");
    if (portEnv == null) {
      System.out.println("INFO: PORT environment variable not set. Using default port 7070.");
      return 7070; // Default port if PORT env var is not set
    }
    try {
      int port = Integer.parseInt(portEnv);
      if (port <= 0 || port > 65535) {
        System.err.println(
            "Warning: Invalid PORT environment variable value '"
                + portEnv
                + "' (must be between 1 and 65535). Using default port 7070.");
        return 7070;
      }
      return port;
    } catch (NumberFormatException e) {
      System.err.println(
          "Warning: Invalid PORT environment variable value '"
              + portEnv
              + "'. Using default port 7070.");
      return 7070; // Default port if parsing fails
    }
  }

  private static final Map<String, String> MIME_TYPE_MAP =
      Map.of(
          ".png", "image/png",
          ".webp", "image/webp",
          ".jpg", "image/jpeg",
          ".jpeg", "image/jpeg", // Added .jpeg as common alternative
          ".js", "text/javascript",
          ".map", "text/javascript", // Source maps often served as application/json or text/javascript
          ".svg", "image/svg+xml",
          ".html", "text/html",
          ".css", "text/css");
  private static final String DEFAULT_MIME_TYPE = "text/plain";

  private static String getMimeType(final String resourcePath) {
    String extension = "";
    int i = resourcePath.lastIndexOf('.');
    if (i > 0) {
      extension = resourcePath.substring(i);
    }

    String mimeType = MIME_TYPE_MAP.getOrDefault(extension.toLowerCase(), DEFAULT_MIME_TYPE);

    // Append charset for text-based types
    if (mimeType.startsWith("text/") || mimeType.equals("application/javascript") || mimeType.equals("image/svg+xml")) {
      return mimeType + "; charset=utf-8";
    }
    return mimeType;
  }

  private static void applyResponseHeaders(final Context ctx, final String resourcePath) {
    var ctype = getMimeType(resourcePath);
    ctx.header("Content-Type", ctype);

    if (ctype.contains("text/html")) {
      ctx.header(
          "Content-Security-Policy",
          "default-src 'self' http:; "
              + "script-src 'self' 'unsafe-eval' 'unsafe-inline' http:; "
              + "style-src 'self' 'unsafe-inline';");

      // "Content-Security-Policy",
      // "default-src 'self' apis.google.com; "
      //     + "script-src 'self' 'unsafe-eval' 'unsafe-inline' http:; "
      //     + "frame-src 'self' infinite-epoch-2900.firebaseapp.com; "
      //     + "font-src 'self' fonts.gstatic.com; "
      //     + "style-src 'self' fonts.googleapis.com 'unsafe-inline'; "
      //     + "connect-src https://identitytoolkit.googleapis.com; ");
    } else if (ctype.contains("image/svg+xml")
        || ctype.contains("image/png")
        || resourcePath.endsWith(".map")) {
      ctx.header("Cache-Control", "max-age=3600");
    }
  }

  /**
   * Handles serving static files (HTML, CSS, JS, images, etc.) from classpath resources. Maps
   * root-relative URLs to the /resources/web directory in the classpath.
   */
  public static void handleStaticFile(final Context ctx) throws IOException {
    String requestedPath = ctx.path();
    // Default to index.html if root path is requested
    String resourcePath = requestedPath.equals("/") ? "/index.html" : requestedPath;
    String jarResourcePath = "/resources/web" + resourcePath;
    System.out.printf("Static request: %s -> %s\n", requestedPath, jarResourcePath);

    InputStream s = AppUtils.getResourceAsStream(jarResourcePath); // Use the mapped path
    if (s == null) {
      // Important for SPA: If a resource isn't found, assume it's an Angular route
      // and serve index.html instead, letting Angular handle the routing.
      // Only do this for GET requests that likely expect HTML.
      if (ctx.method().equals(io.javalin.http.HandlerType.GET) && !requestedPath.contains(".")) {
        System.out.println("Assuming SPA route, serving index.html for: " + requestedPath);
        InputStream indexStream = AppUtils.getResourceAsStream("/resources/web/index.html");
        if (indexStream != null) {
          String indexContent = new String(indexStream.readAllBytes(), StandardCharsets.UTF_8);
          ctx.status(200);
          applyResponseHeaders(ctx, "index.html");
          ctx.result(indexContent);
          return;
        }
        System.err.println("FATAL: index.html not found in classpath resources!");
        ctx.status(404).header("Content-Type", "text/plain").result("Not found");
        return;
      }

      System.out.printf("Static resource not found: %s\n", jarResourcePath);
      ctx.status(404).header("Content-Type", "text/plain").result("Not found");
      return;
    }

    // String content = new String(s.readAllBytes(), StandardCharsets.UTF_8);
    ctx.status(200);
    applyResponseHeaders(ctx, resourcePath);
    ctx.result(s);
  }

  public static void main(String[] args) {
    try {
      Map<String, Object> appSettings = StateService.getInstance().getSettings();
      int port = getPort();
      SessionManager sessionManager = new SessionManager();
      JwtValidator jwtValidator = new JwtValidator(appSettings);
      AuthController authController = new AuthController(jwtValidator, sessionManager);
      ApigeeController apigee = new ApigeeController(appSettings);

      var app =
          Javalin.create(
              config -> {
                // One can use this cheap/cheery logging, OR the devlogging (quite verbose).
                // Not both.
                //
                // config.requestLogger.http(
                //     (ctx, ms) -> {
                //       // this gets called after the request has been handled.
                //       System.out.printf(
                //           "%s %s => %d (%.2f ms)\n",
                //           ctx.method(), ctx.path(), ctx.status().getCode(), ms);
                //     });
                config.bundledPlugins.enableDevLogging();
                // config.http.disableCompression();

                // NOTE:
                // Serving static files this way didn't work and attempts to
                // diagnose it led me down numerous blind alleys, which cost me
                // hours.
                //
                // config.staticFiles.add(
                //     staticFiles -> {
                //       staticFiles.hostedPath = "/static";
                //       // the directory where the static files are located
                //       staticFiles.directory = "/resources/web";
                //       staticFiles.location = Location.CLASSPATH;
                //       staticFiles.precompress = false;
                //
                //       // you can configure this to enable symlinks (=
                //       // ContextHandler.ApproveAliases())
                //       staticFiles.aliasCheck = null;
                //
                //       // staticFiles.headers = Map.of(...);
                //
                //       //  skip files that begin with dot
                //       staticFiles.skipFileFunction = req ->
                // req.getPathInfo().startsWith(".");
                //
                //       // can add custom mimetypes for extensions
                //       // staticFiles.mimeTypes.add(mimeType, ext);
                //
                //     });

                // Enable CORS for development (allow requests from Angular dev server, e.g.,
                // http://localhost:4200)
                // IMPORTANT: Restrict this in production environments!
                config.bundledPlugins.enableCors(
                    cors -> {
                      cors.addRule(
                          it -> {
                            it.allowHost("example.com", "javalin.io");
                          });
                    });

                // Optional: Enable webjars if you plan to use them
                // config.staticFiles.enableWebjars();

                config.router.apiBuilder(
                    () -> { // sets a static variable scoped to the lambda
                      path(
                          "/api",
                          () -> {
                            get(
                                "/",
                                ctx ->
                                    ctx.contentType("text/plain")
                                        .status(404)
                                        .result("use a collection path"));

                            // get(
                            //     "/hello",
                            //     ctx -> ctx.json("{\"message\": \"Hello from Javalin
                            // Backend!\"}"));

                            get("/apiproducts", apigee::getAllApiProducts);
                            get("/me/apps/{appname}", apigee::getDeveloperAppDetails);
                            get("/me/apps", apigee::getDeveloperApps);
                            post("/me/apps", apigee::createDeveloperApp);
                            delete("/me/apps/{appname}", apigee::deleteDeveloperApp);
                            get("/me", apigee::getDeveloperDetails);
                            post("/registerSelfAsDeveloper", apigee::createNewDeveloper);
                            // post("/me/attributes", apigee::updateDeveloperAttributes);
                            post("/me/certificates", apigee::registerCertificate);
                            delete("/me/certificates/{certId}", apigee::deregisterCertificate);

                            // get(
                            //   "/apiproducts/{id}",
                            //     apigee::getApiProduct);

                          });

                      // Authentication routes
                      path(
                          "/api/auth",
                          () -> {
                            post("/login", authController::login);
                            post("/logout", authController::logout);
                          });
                    });
              })
          // Remove the specific .error(404, ...) handler as handleStaticFile now handles it
          // .error(404, ctx -> ctx.result("Not Found"))
          ; // End of Javalin.create() chain

      // --- .before filter for protected API routes ---
      app.before(
          "/api/*", // Apply to all /api routes except /api/auth/**
          ctx -> {
            // Allow auth routes to pass through
            if (ctx.path().startsWith("/api/auth/")) {
              return;
            }
            // Check for valid session
            sessionManager
                .getSession(ctx)
                .ifPresentOrElse(
                    session -> {
                      // Session is valid, add user info to context
                      ctx.attribute("userEmail", session.email());
                      ctx.attribute("userId", session.userId());
                      ctx.attribute("name", session.name()); // Add first name
                      ctx.attribute("firstName", session.firstName()); // Add first name
                      ctx.attribute("lastName", session.lastName()); // Add last name
                      System.out.printf(
                          "Session valid for %s (%s %s), allowing request to %s\n",
                          session.email(), session.firstName(), session.lastName(), ctx.path());
                    },
                    () -> {
                      // No valid session, halt with 401
                      System.out.printf("No valid session found for request to %s.\n", ctx.path());
                      ctx.status(HttpStatus.UNAUTHORIZED);
                      throw new UnauthorizedResponse();
                      // .json(Map.of("error", "Authentication required."))
                      // .result(); // Halt execution
                    });
          });

      // --- Static file handler (must be after API routes and any ".before" filters) ---
      app.get("/*", App::handleStaticFile);

      app.exception(
          Exception.class,
          (e, ctx) -> {
            e.printStackTrace();
            System.out.printf("Exception %s %s => %s\n", ctx.method(), ctx.path(), e.getMessage());
            ctx.json("Bad request: ${e.message}.").status(500);
          });

      // Start the server after configuration and routes are defined
      app.start(port);

      System.out.println("Server started. Listening on http://localhost:" + port);
      System.out.println("Frontend should be accessible at http://localhost:" + port);
      System.out.println("API base path: /api");

      // Optional: Add a default handler for the root path if spaRoot isn't sufficient
      // app.get("/", ctx -> ctx.result("Welcome to the Javalin Backend! Static files should be
      // served."));
    } catch (java.lang.Exception exc1) {
      System.out.println("Exception:" + exc1.toString());
      exc1.printStackTrace();
      throw new RuntimeException("uncaught exception", exc1);
    }
  }
}
