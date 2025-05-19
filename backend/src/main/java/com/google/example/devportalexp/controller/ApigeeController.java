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

import com.google.example.devportalexp.AppUtils;
import com.google.example.devportalexp.KeyUtility;
import com.google.example.devportalexp.model.ApiProduct;
import com.google.example.devportalexp.service.CacheService;
import com.google.example.devportalexp.service.StateService;
import com.google.example.devportalexp.service.X509CertificateService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.javalin.http.Context;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApigeeController {
  public static final int MAX_CERTIFICATES = 4;
  public static final int MAX_DEVELOPER_APPS = 4;
  private static final Logger log = LoggerFactory.getLogger(ApigeeController.class);
  private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private static final Type mapType = new TypeToken<HashMap<String, Object>>() {}.getType();
  private Map<String, Object> appSettings;

  public ApigeeController(Map<String, Object> appSettings)
      throws IOException, InterruptedException, URISyntaxException {
    this.appSettings = appSettings;

    CacheService.getInstance()
        .registerLoader(
            (key) -> key.endsWith("token"), (_ignoredKey) -> this.loadGcpAccessToken(_ignoredKey))
        .registerLoader((key) -> key.endsWith("products"), (_ignoredKey) -> this.loadProducts());
  }

  /**
   * Cache loader function to retrieve a GCP access token. Checks if running in Cloud Run to
   * determine the token retrieval method.
   *
   * @param ignoredKey The cache key (ignored in this implementation).
   * @return The access token as a String, or null if an error occurs.
   */
  private Object loadGcpAccessToken(String ignoredKey) {
    if (StateService.isRunningInCloud()) {
      System.out.println("Running in Cloud Run, fetching token from metadata server...");
      String metadataUrl =
          "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token";
      try {
        String responseBody = fetch(metadataUrl, "GET", Map.of("Metadata-Flavor", "Google"), null);
        if (responseBody != null) {
          Map<String, Object> tokenResponse = gson.fromJson(responseBody, mapType);
          if (tokenResponse != null && tokenResponse.containsKey("access_token")) {
            String accessToken = (String) tokenResponse.get("access_token");
            System.out.println("Successfully fetched token from metadata server.");
            return accessToken;
          }
        }
      } catch (Exception e) {
        System.err.println(
            "Unexpected error fetching/parsing token from metadata server: " + e.getMessage());
        e.printStackTrace();
      }
      return null;
    }
    System.out.println("Not running in Cloud Run, using gcloud for token...");
    return executeCommand(
        "gcloud",
        "auth",
        "print-access-token",
        "--project",
        (String) appSettings.get("project"),
        "--quiet");
  }

  private static String fetch(
      String uri, String method, Map<String, String> requestHeaders, Map<String, Object> payload)
      throws URISyntaxException, IOException, InterruptedException {
    System.out.printf("*** fetch [%s %s]...\n", method, uri);

    HttpRequest.Builder builder = HttpRequest.newBuilder().uri(new URI(uri));
    if (requestHeaders != null) {
      for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
        builder.header(entry.getKey(), entry.getValue());
      }
    }

    if ("GET".equals(method)) {
      builder = builder.GET();
    } else if ("POST".equals(method)) {
      builder =
          builder
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)));
    } else if ("DELETE".equals(method)) {
      builder = builder.DELETE();
    } else {
      throw new RuntimeException("HTTP method not supported: " + method);
    }

    HttpRequest request = builder.build();
    HttpClient client = HttpClient.newHttpClient();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    HttpHeaders responseHeaders = response.headers();
    System.out.printf("response headers:\n%s\n", responseHeaders.toString());
    String body = response.body();
    System.out.printf("\n\n=>\n%s\n", body);
    return body;
  }

  private Map<String, Object> apigeeFetch(String path, String method, Map<String, Object> payload)
      throws URISyntaxException, IOException, InterruptedException {
    String apigeeProject = (String) appSettings.get("project");
    String uri =
        String.format("https://apigee.googleapis.com/v1/organizations/%s%s", apigeeProject, path);
    String apigeeOrgToken = (String) CacheService.getInstance().get("apigeetoken");
    String stringResult =
        fetch(uri, method, Map.of("Authorization", "Bearer " + apigeeOrgToken), payload);
    Map<String, Object> json = gson.fromJson(stringResult, mapType);
    return json;
  }

  private Map<String, Object> apigeeGet(String path)
      throws URISyntaxException, IOException, InterruptedException {
    return apigeeFetch(path, "GET", null);
  }

  private Map<String, Object> apigeePost(String path, Map<String, Object> payload)
      throws URISyntaxException, IOException, InterruptedException {
    return apigeeFetch(path, "POST", payload);
  }

  private static String yamlSpecPath(String productName) {
    //  "/specs/weather-v1.yaml";
    // for now this is contrived.
    // TODO: rely on custom attr for the API product to find the spec.
    return "/specs/" + productName.replaceAll(" ", "-") + ".yaml";
  }

  /**
   * Executes an external command and returns its standard output as a String. This method blocks
   * until the command completes.
   *
   * @param command The command and its arguments to execute (e.g., "gcloud", "auth",
   *     "print-identity-token").
   * @return The standard output of the command, trimmed of leading/trailing whitespace.
   * @throws IOException If an I/O error occurs during process creation or stream reading.
   * @throws InterruptedException If the current thread is interrupted while waiting for the process
   *     to complete.
   * @throws RuntimeException If the command execution fails (non-zero exit code).
   */
  private static String executeCommand(String... command) { // Changed to static method
    try {

      ProcessBuilder processBuilder = new ProcessBuilder(command);
      System.out.println(
          "Executing command: " + String.join(" ", command)); // Log the command being run

      Function<InputStream, String> slurp =
          inputStream -> {
            try {
              StringBuilder output = new StringBuilder();
              // Use try-with-resources to ensure the reader is closed automatically
              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  output.append(line).append(System.lineSeparator()); // Append lines as they come
                }
              }
              return output.toString().trim();
            } catch (java.lang.Exception exc1) {
              System.out.println("Exception:" + exc1.toString());
              exc1.printStackTrace();
              return null;
            }
          };

      Process process = processBuilder.start();
      String output = slurp.apply(process.getInputStream());
      boolean finished = process.waitFor(60, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new RuntimeException("Command timed out: " + String.join(" ", command));
      }
      int exitCode = process.exitValue();

      if (exitCode != 0) {
        // Read the error stream for more details if the command failed
        String errorOutput = slurp.apply(process.getErrorStream());
        throw new RuntimeException(
            String.format(
                "Command failed with exit code %d. [%s] error:%s",
                exitCode, String.join(" ", command), errorOutput));
      }

      return output;
    } catch (java.lang.Exception exc1) {
      System.out.println("Exception:" + exc1.toString());
      exc1.printStackTrace();
    }
    return null;
  }

  private List<ApiProduct> loadProducts() {
    try {
      Map<String, Object> productResponse = apigeeGet("/apiproducts?expand=false");
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> productList =
          (List<Map<String, Object>>) productResponse.get("apiProduct");
      // result is List<ApiProduct> apiProducts = new ArrayList<>();
      return productList.stream()
          .map(
              rec -> {
                String productName = (String) rec.get("name");
                return new ApiProduct(
                    productName,
                    productName + " API",
                    "Provides some information.",
                    yamlSpecPath(productName));
              })
          .collect(Collectors.toList());
    } catch (java.lang.Exception exc1) {
      System.out.println("Exception:" + exc1.toString());
      exc1.printStackTrace();
      throw new RuntimeException("uncaught exception", exc1);
    }
  }

  /** GET /api/apiproducts */
  public void getAllApiProducts(final Context ctx) {
    System.out.println("GET /apiproducts");
    @SuppressWarnings("unchecked")
    List<ApiProduct> apiProducts = (List<ApiProduct>) CacheService.getInstance().get("apiproducts");
    ctx.json((apiProducts != null) ? apiProducts : Collections.emptyList());
  }

  /** GET /api/myapps */
  public void getDeveloperApps(final Context ctx)
      throws IOException, InterruptedException, URISyntaxException {
    // Session is valid, add user info to context if needed
    System.out.println("GET /myapps");
    String devEmail = ctx.attribute("userEmail");
    Map<String, Object> devResponse = apigeeGet("/developers/" + devEmail);
    @SuppressWarnings("unchecked")
    List<String> appList = (List<String>) devResponse.get("apps");
    ctx.json((appList != null) ? appList : Collections.emptyList());
  }

  /** GET /api/myapps/{appname} */
  public void getDeveloperAppDetails(final Context ctx)
      throws IOException, InterruptedException, URISyntaxException {
    String appName = ctx.pathParam("appname");
    String devEmail = ctx.attribute("userEmail");

    if (devEmail == null || devEmail.isBlank()) {
      // This shouldn't happen if the before filter is working correctly
      System.err.println("Error: userEmail not found in context for getDeveloperApp.");
      ctx.status(500).json("Internal server error: User email not found.");
      return;
    }
    System.out.printf("fetch app details for userEmail %s app %s\n", devEmail, appName);
    Map<String, Object> appDetails =
        apigeeGet(String.format("/developers/%s/apps/%s", devEmail, appName));
    ctx.json(appDetails);
  }

  /**
   * Calculates the minimum key expiry time in seconds based on the 'max-key-lifetime' attribute of
   * the API products listed in the payload. Validates the product list size. Sets appropriate error
   * responses on the context if validation fails or an error occurs.
   *
   * @param apiProducts The validated list of API product names.
   * @return An Optional containing the minimum expiry time in seconds (-1 for infinite), or
   *     Optional.empty() if an error occurs during processing (e.g., fetching product details).
   */
  private Optional<Long> calculateMinimumKeyExpirySeconds(List<String> apiProducts) {
    // Assumes apiProducts list is non-null, non-empty, and size-validated by the caller.

    System.out.printf("Processing %d API products for key expiry...\n", apiProducts.size());
    long minExpirySeconds = -1L; // -1 indicates no limit found yet or infinite

    for (String productName : apiProducts) {
      try {
        System.out.printf("Fetching details for API product: %s\n", productName);
        Map<String, Object> productDetails = apigeeGet("/apiproducts/" + productName);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attributes =
            (List<Map<String, Object>>) productDetails.get("attributes");

        if (attributes != null) {
          Optional<String> lifetimeValue =
              attributes.stream()
                  .filter(
                      attr ->
                          "max-key-lifetime".equals(attr.get("name"))
                              && attr.get("value") instanceof String)
                  .map(attr -> (String) attr.get("value"))
                  .findFirst();

          if (lifetimeValue.isPresent()) {
            String timespanStr = lifetimeValue.get();
            System.out.printf(
                "Found 'max-key-lifetime' attribute for product %s: %s\n",
                productName, timespanStr);
            try {
              long currentProductExpirySeconds = parseTimespanToSeconds(timespanStr);
              System.out.printf(
                  "Parsed expiry for %s to %d seconds\n", productName, currentProductExpirySeconds);

              if (currentProductExpirySeconds != -1) { // If the current product has a limit
                if (minExpirySeconds == -1
                    || currentProductExpirySeconds < minExpirySeconds) { // If it's the first limit
                  // found or smaller than
                  // current min
                  minExpirySeconds = currentProductExpirySeconds;
                  System.out.printf("New minimum expiry set to %d seconds\n", minExpirySeconds);
                }
              }
              // If currentProductExpirySeconds is -1 (no limit for this product), it doesn't
              // affect the minimum unless minExpirySeconds is still -1.
            } catch (IllegalArgumentException e) {
              System.err.printf(
                  "Warning: Could not parse 'max-key-lifetime' value '%s' for product %s: %s\n",
                  timespanStr, productName, e.getMessage());
              // Currently ignoring parse errors and proceeding.
            }
          } else {
            System.out.printf(
                "No 'max-key-lifetime' attribute found for product %s. Assuming no limit.\n",
                productName);
          }
        } else {
          System.out.printf(
              "No attributes found for product %s. Assuming no limit.\n", productName);
        }
      } catch (Exception e) {
        System.err.printf(
            "Error fetching or processing details for API product %s: %s\n",
            productName, e.getMessage());
        // Fail fast if product details cannot be fetched/processed
        // Return empty optional to signal error to the caller.
        return Optional.empty();
      }
    }
    return Optional.of(minExpirySeconds); // Return calculated minimum expiry
  }

  /** POST /api/myapps */
  public void createDeveloperApp(final Context ctx)
      throws IOException, InterruptedException, URISyntaxException {
    String devEmail = ctx.attribute("userEmail");

    if (devEmail == null || devEmail.isBlank()) {
      // This shouldn't happen if the before filter is working correctly
      System.err.println("Error: userEmail not found in context.");
      ctx.status(500).json("Internal server error: User email not found.");
      return;
    }

    // Verify Content-Type
    String contentType = ctx.contentType();
    if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
      System.err.printf("Error: Invalid Content-Type: %s\n", contentType);
      ctx.status(415).json(Map.of("error", "Request must be application/json"));
      return;
    }

    // Parse JSON payload into a Map
    Map<String, Object> payloadMap;

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> castedValue = (Map<String, Object>) ctx.bodyAsClass(Map.class);
      payloadMap = castedValue;

    } catch (Exception e) {
      System.err.printf(
          "Error parsing create app request body or extracting fields: %s\n", e.getMessage());
      ctx.status(400).json(Map.of("error", "Invalid JSON payload or structure"));
      return;
    }

    // --- App Limit Check ---
    try {
      String appsUri = String.format("/developers/%s/apps", devEmail);
      Map<String, Object> currentAppsResponse = apigeeGet(appsUri);
      @SuppressWarnings("unchecked")
      List<String> currentAppList = (List<String>) currentAppsResponse.get("app");
      int appCount = (currentAppList != null) ? currentAppList.size() : 0;

      if (appCount >= MAX_DEVELOPER_APPS) {
        System.err.printf(
            "Error: Developer %s already has %d apps (limit is %d).\n",
            devEmail, appCount, MAX_DEVELOPER_APPS);
        ctx.status(400)
            .json(
                Map.of(
                    "error",
                    String.format(
                        "Maximum number of developer apps (%d) already registered.",
                        MAX_DEVELOPER_APPS)));
        return;
      }
      System.out.printf(
          "Developer %s has %d apps, proceeding with app creation.\n", devEmail, appCount);

    } catch (Exception e) {
      // Handle potential errors during the app count check
      System.err.printf(
          "Error checking app count for developer %s: %s\n", devEmail, e.getMessage());
      ctx.status(500).json(Map.of("error", "Failed to verify current app count."));
      return;
    }
    // --- End App Limit Check ---

    // --- API Product Validation ---
    List<String> apiProducts = null;
    Object apiProductsObj = payloadMap.get("apiProducts");
    if (apiProductsObj instanceof List) {
      // Check if all elements are Strings
      if (((List<?>) apiProductsObj).stream().allMatch(item -> item instanceof String)) {
        @SuppressWarnings("unchecked")
        List<String> castedList = (List<String>) apiProductsObj;
        apiProducts = castedList;
      }
    }

    // Validate presence and count (1 to 5)
    if (apiProducts == null || apiProducts.isEmpty()) {
      System.err.println("Error: 'apiProducts' field is missing, empty, or not a list of strings.");
      ctx.status(400).json(Map.of("error", "Request must include a non-empty 'apiProducts' list."));
      return;
    }

    if (apiProducts.size() > 5) {
      System.err.printf(
          "Error: Too many API products requested (%d). Maximum allowed is 5.\n",
          apiProducts.size());
      ctx.status(400)
          .json(
              Map.of(
                  "error",
                  String.format(
                      "Too many API products selected (%d). Maximum allowed is 5.",
                      apiProducts.size())));
      return;
    }
    // --- End API Product Validation ---

    // Calculate minimum key expiry based on selected API products
    Optional<Long> minExpiryOptional = calculateMinimumKeyExpirySeconds(apiProducts);
    if (minExpiryOptional.isEmpty()) {
      // The cause will have been logged in the helper method.
      // We return 500 here as the overall operation failed.
      ctx.status(500).json(Map.of("error", "Failed to process API product details."));
      return;
    }

    // Add keyExpiresIn to the payload if a minimum finite expiry was found
    long minExpirySeconds = minExpiryOptional.get();
    if (minExpirySeconds != -1) {
      System.out.printf(
          "Setting 'keyExpiresIn' in request payload to %d seconds.\n", minExpirySeconds);
      payloadMap.put("keyExpiresIn", String.valueOf(minExpirySeconds * 1000));
    }

    // Ensure standard attributes are present
    payloadMap.putIfAbsent(
        "attributes", List.of(Map.of("name", "createdBy", "value", "devportal-exp")));

    // Create the developer app.
    Map<String, Object> appDetails =
        apigeePost(String.format("/developers/%s/apps", devEmail), payloadMap);
    ctx.status(201).json(appDetails);
  }

  /** GET /api/me */
  public void getDeveloperDetails(final Context ctx)
      throws IOException, InterruptedException, URISyntaxException {
    String devEmail = ctx.attribute("userEmail");
    if (devEmail == null || devEmail.isBlank()) {
      log.warn("Error: userEmail not found in context.");
      ctx.status(500).json("Internal server error: User email not found.");
      return;
    }
    try {
      String uri = String.format("/developers/%s", devEmail);
      Map<String, Object> devDetails = apigeeGet(uri);
      if (devDetails.containsKey("error")) {
        // TODO: be more thorough, check for error code 404 from Apigee API
        log.warn(
            String.format(
                "Error retrieving developer details for %s: %s", devEmail, devDetails.toString()));
        ctx.status(404).json(Collections.emptyMap());
        return;
      }
      Map<String, Object> devAttrs = apigeeGet(uri + "/attributes");
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> attrList = (List<Map<String, Object>>) devAttrs.get("attribute");
      if (attrList == null) {
        devDetails.put("attribute", Collections.emptyList());
        devDetails.put("certificates", Collections.emptyList());
      } else {
        Map<Boolean, List<Map<String, Object>>> partitionedMap = partitionByCert(attrList);
        List<Map<String, Object>> otherAttrs =
            partitionedMap.getOrDefault(false, Collections.emptyList());
        devDetails.put("attribute", otherAttrs);

        List<Map<String, Object>> certs =
            partitionedMap.getOrDefault(true, Collections.emptyList());
        List<Map<String, String>> xformedCerts =
            certs.stream()
                .map(
                    originalMap ->
                        Map.of(
                            "id", (String) originalMap.get("name"),
                            "fingerprint", (String) originalMap.get("value")))
                .collect(Collectors.toList());
        devDetails.put("certificates", xformedCerts);
      }
      ctx.status(200).json(devDetails);
    } catch (Exception e) {
      log.warn(String.format("Exception while getting details for %s", devEmail));
      e.printStackTrace();
      ctx.status(500).json(Map.of("error", "Invalid JSON payload or structure"));
      return;
    }
  }

  private static Map<Boolean, List<Map<String, Object>>> partitionByCert(
      List<Map<String, Object>> attrList) {
    Predicate<Map<String, Object>> startsWithCert =
        mapEntry -> {
          if (mapEntry == null) return false;
          Object nameValue = mapEntry.get("name");
          return nameValue instanceof String && ((String) nameValue).startsWith("cert-");
        };

    // Use Collectors.partitioningBy to split the stream into two lists based on the predicate
    Map<Boolean, List<Map<String, Object>>> partitionedMap =
        attrList.stream().collect(Collectors.partitioningBy(startsWithCert));

    return partitionedMap;
  }

  // /** POST /api/me/attributes */
  // public void updateDeveloperAttributes(final Context ctx)
  //     throws IOException, InterruptedException, URISyntaxException {
  //   String devEmail = ctx.attribute("userEmail");
  //   if (devEmail == null || devEmail.isBlank()) {
  //     System.err.println("Error: userEmail not found in context.");
  //     ctx.status(500).json("Internal server error: User email not found.");
  //     return;
  //   }
  //
  //   // Verify Content-Type
  //   String contentType = ctx.contentType();
  //   if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
  //     System.err.printf("Error: Invalid Content-Type: %s\n", contentType);
  //     ctx.status(415).json(Map.of("error", "Request must be application/json"));
  //     return;
  //   }
  //
  //   // Parse JSON payload into a Map
  //   List<Map<String, Object>> attrs = null;
  //   try {
  //     @SuppressWarnings("unchecked")
  //     Map<String, Object> map = (Map<String, Object>) ctx.bodyAsClass(Map.class);
  //
  //     // Extract and validate data from the Map
  //     Object attrObj = map.get("attribute");
  //
  //     if (attrObj instanceof List) {
  //       if (((List<?>) attrObj).stream().allMatch(item -> item instanceof Map)) {
  //         @SuppressWarnings("unchecked")
  //         List<Map<String, Object>> casted = (List<Map<String, Object>>) attrObj;
  //         attrs = casted;
  //       }
  //     }
  //   } catch (Exception e) {
  //     e.printStackTrace();
  //   }
  //
  //   if (attrs == null) {
  //     System.err.printf("Error parsing payload\n");
  //     ctx.status(400).json(Map.of("error", "bad payload"));
  //     return;
  //   }
  //
  //   try {
  //     // Map<String,Object> payload = new HashMap<>();
  //     // payload.put("attribute", attrs);
  //     String uri = String.format("/developers/%s/attributes", devEmail);
  //     Map<String, Object> updatedAttrs = apigeePost(uri, Map.of("attribute", attrs));
  //     ctx.status(200).json(updatedAttrs);
  //   } catch (Exception e) {
  //     e.printStackTrace();
  //     ctx.status(500).json(Map.of("error", "unhandled error"));
  //     return;
  //   }
  // }

  /**
   * POST /api/registerSelfAsDeveloper
   *
   * <p>Create/register a new developer in Apigee.
   */
  public void createNewDeveloper(final Context ctx)
      throws IOException, InterruptedException, URISyntaxException {
    String devEmail = ctx.attribute("userEmail");
    String firstName = ctx.attribute("firstName");
    String lastName = ctx.attribute("lastName");

    if (devEmail == null || devEmail.isBlank() || firstName == null || lastName == null) {
      System.err.println("Error: User details (email, firstName, lastName) not found in context.");
      ctx.status(500).json("Internal server error: User details not found in session.");
      return;
    }

    // Generate unique username
    String userName =
        String.format(
            "%s-%s-%04d",
            firstName.toLowerCase(), lastName.toLowerCase(), new java.util.Random().nextInt(10000));

    System.out.printf(
        "Creating developer: email=%s, firstName=%s, lastName=%s, userName=%s\n",
        devEmail, firstName, lastName, userName);

    try {
      // Use retrieved/generated values in the payload
      Map<String, Object> requestPayload =
          Map.of(
              "email", devEmail,
              "firstName", firstName,
              "lastName", lastName,
              "userName", userName);

      Map<String, Object> responsePayload = apigeePost("/developers", requestPayload);

      // --------------------------------------------
      // Set a default partner company name
      String uri = String.format("/developers/%s/attributes", devEmail);
      List<Map<String, Object>> attrlist = new ArrayList<>();
      attrlist.add(
          Map.of(
              "name",
              "partner-name",
              "value",
              String.format("CymbalPartner %04d LLC", (new Random()).nextInt(10000))));
      apigeePost(uri, Map.of("attribute", attrlist));
      // --------------------------------------------
      ctx.status(201).json(responsePayload);
    } catch (Exception e) {
      e.printStackTrace();
      ctx.status(500).json(Map.of("error", "unhandled error"));
      return;
    }
  }

  /**
   * POST /api/me/certificates
   *
   * <p>Expects a json with these members: publicKey - PEM-encoded public key keyId - arbitrary
   * string identifying the key
   */
  public void registerCertificate(final Context ctx)
      throws IOException, InterruptedException, URISyntaxException {
    String devEmail = ctx.attribute("userEmail");
    String userName = ctx.attribute("name");
    if (devEmail == null || devEmail.isBlank()) {
      System.err.println("Error: userEmail not found in context.");
      ctx.status(500).json("Internal server error: User email not found.");
      return;
    }
    System.out.printf("registerCertificate [%s]...\n", devEmail);

    // Verify Content-Type
    String contentType = ctx.contentType();
    if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
      System.err.printf("Error: Invalid Content-Type: %s\n", contentType);
      ctx.status(415).json(Map.of("error", "Request must be application/json"));
      return;
    }

    Type t = new TypeToken<Map<String, Object>>() {}.getType();
    Map<String, Object> body = gson.fromJson(ctx.body(), t);
    if (body == null) {
      ctx.status(400).json(Map.of("error", "payload cannot be parsed"));
      return;
    }
    boolean hasPublicKey = body.containsKey("publicKey");
    boolean hasCertificate = body.containsKey("certificate");

    boolean invalidPayload =
        (!body.containsKey("keyId") && hasPublicKey) || (hasPublicKey == hasCertificate);

    if (invalidPayload) {
      ctx.status(400)
          .json(Map.of("error", "invalid json payload, missing or inconsistent properties"));
      return;
    }

    System.out.printf("registerCertificate payload looks good...\n");

    try {
      // --- Certificate Limit Check ---
      String uri = String.format("/developers/%s/attributes", devEmail);
      Map<String, Object> currentDevAttrs = apigeeGet(uri);
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> currentAttrList =
          (List<Map<String, Object>>) currentDevAttrs.get("attribute");

      long certificateCount = 0;
      if (currentAttrList != null) {
        certificateCount =
            currentAttrList.stream()
                .filter(
                    mapEntry -> {
                      if (mapEntry == null) return false;
                      Object nameValue = mapEntry.get("name");
                      return nameValue instanceof String
                          && ((String) nameValue).startsWith("cert-");
                    })
                .count();
      }

      if (certificateCount >= MAX_CERTIFICATES) {
        System.err.printf(
            "Error: Developer %s already has %d certificates (limit is %d).\n",
            devEmail, certificateCount, MAX_CERTIFICATES);
        ctx.status(400)
            .json(
                Map.of(
                    "error",
                    String.format(
                        "Maximum number of certificates (%d) already registered.",
                        MAX_CERTIFICATES)));
        return;
      }
      System.out.printf(
          "Developer %s has %d certificates, proceeding with registration.\n",
          devEmail, certificateCount);
      // --- End Certificate Limit Check ---

      String certificatePem = null;
      X509Certificate signedCertificate = null;

      if (hasPublicKey) {
        // generate a certificate from an uploaded public key
        // --- Determine Organization Name for SubjectDN ---
        String partnerOrgName = "Unknown Partner Org";
        if (currentAttrList != null) {
          partnerOrgName =
              currentAttrList.stream()
                  .filter(
                      attr ->
                          attr != null
                              && "partner-name".equals(attr.get("name"))
                              && attr.get("value") instanceof String)
                  .map(attr -> (String) attr.get("value"))
                  .findFirst()
                  .orElse(partnerOrgName);
        }
        System.out.printf("Using partner organization name: %s\n", partnerOrgName);
        // --- End Determine Organization Name ---

        String subjectDN =
            String.format(
                "CN=%s, O=%s, serialNumber=%s",
                userName, partnerOrgName, (String) body.get("keyId"));

        signedCertificate =
            X509CertificateService.getInstance()
                .generateNewSignedCertificate(
                    KeyUtility.decodePublicKey((String) body.get("publicKey")),
                    subjectDN,
                    devEmail,
                    partnerOrgName);
        certificatePem = KeyUtility.toPem(signedCertificate);
      } else {
        // user is uploading a previously-generated certifcate
        certificatePem = (String) body.get("certificate");
        signedCertificate = KeyUtility.decodeCertificate(certificatePem);
        X509CertificateService.enforceClientCertificateConstraints(signedCertificate);
      }

      // Fetch attrs again (to be safe)
      currentDevAttrs = apigeeGet(uri);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> attrlist =
          (List<Map<String, Object>>) currentDevAttrs.get("attribute");
      if (attrlist == null) {
        attrlist = new ArrayList<>();
      }
      String fingerprint = KeyUtility.fingerprintBase64(signedCertificate);
      verifyFingerprintUniqueness(fingerprint, attrlist);
      String identifier = String.format("cert-%s", nowAsYyyyMmDdHHmmss());
      attrlist.add(Map.of("name", identifier, "value", fingerprint));
      System.out.printf("registerCertificate updating dev attrs...\n");
      apigeePost(uri, Map.of("attribute", attrlist));
      System.out.printf("registerCertificate updating dev attrs...DONE.\n");

      Map<String, Object> response =
          Map.of(
              "pem",
              certificatePem,
              "fingerprint",
              fingerprint,
              "certificate-id",
              identifier,
              "subjectDN",
              signedCertificate.getSubjectX500Principal().toString(),
              "notBefore",
              certDate(signedCertificate.getNotBefore()),
              "notAfter",
              certDate(signedCertificate.getNotAfter()));
      ctx.status(200).json(response);
    } catch (java.lang.IllegalArgumentException | KeyUtility.KeyParseException e) {
      ctx.status(400).json(Map.of("error", e.getMessage()));
      return;
    } catch (Exception e) {
      e.printStackTrace();
      ctx.status(500).json(Map.of("error", "unhandled error"));
      return;
    }
  }

  /**
   * Verifies that the given fingerprint does not already exist as the value of a certificate
   * attribute (one whose name starts with "cert-") in the provided list.
   *
   * @param fingerprint The fingerprint to check for uniqueness.
   * @param attrlist The list of developer attributes.
   * @throws IllegalArgumentException if a duplicate fingerprint is found.
   */
  private static void verifyFingerprintUniqueness(
      String fingerprint, List<Map<String, Object>> attrlist) {
    if (attrlist == null || fingerprint == null) {
      return; // Nothing to check against or no fingerprint provided
    }

    for (Map<String, Object> mapEntry : attrlist) {
      if (mapEntry == null) {
        continue;
      }
      Object nameValue = mapEntry.get("name");
      Object valueValue = mapEntry.get("value");

      if (nameValue instanceof String && ((String) nameValue).startsWith("cert-")) {
        if (fingerprint.equals(valueValue)) {
          throw new IllegalArgumentException(
              String.format(
                  "Certificate with fingerprint '%s' already exists (attribute name: %s).",
                  fingerprint, nameValue));
        }
      }
    }
  }

  /** Handles DELETE /api/me/certificates/{certId} */
  public void deregisterCertificate(final Context ctx)
      throws IOException, InterruptedException, URISyntaxException {
    String devEmail = ctx.attribute("userEmail");
    if (devEmail == null || devEmail.isBlank()) {
      System.err.println("Error: userEmail not found in context.");
      ctx.status(500).json("Internal server error: User email not found.");
      return;
    }
    String certId = ctx.pathParam("certId");
    System.out.printf("deregisterCertificate [%s %s]...\n", devEmail, certId);

    try {
      // get and put
      String uri = String.format("/developers/%s/attributes", devEmail);
      Map<String, Object> devAttrResponse = apigeeGet(uri);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> attrList =
          (List<Map<String, Object>>) devAttrResponse.get("attribute");
      if (attrList == null) {
        // nothing to delete
        ctx.status(200).json(Collections.emptyMap());
        return;
      }

      List<Map<String, Object>> attrsToKeep =
          attrList.stream()
              .filter(
                  mapEntry -> {
                    if (mapEntry == null) return false;
                    Object nameValue = mapEntry.get("name");
                    return nameValue instanceof String && !(((String) nameValue).equals(certId));
                  })
              .collect(Collectors.toList());

      apigeePost(uri, Map.of("attribute", attrsToKeep));
      ctx.status(200).json(Collections.emptyMap());
    } catch (Exception e) {
      e.printStackTrace();
      ctx.status(500).json(Map.of("error", "unhandled error"));
      return;
    }
  }

  private static String certDate(Date d) {
    Instant instant = d.toInstant();
    return DateTimeFormatter.ISO_INSTANT.format(instant);
  }

  private static String nowAsYyyyMmDdHHmmss() {
    Instant instant = Instant.now();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    ZoneId utcZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = instant.atZone(utcZone);
    return zonedDateTime.format(formatter);
  }

  /** DELETE /api/myapps/{appname} */
  public void deleteDeveloperApp(final Context ctx)
      throws IOException, InterruptedException, URISyntaxException {
    String appName = ctx.pathParam("appname");
    String devEmail = ctx.attribute("userEmail");

    if (devEmail == null || devEmail.isBlank()) {
      System.err.println("Error: userEmail not found in context for deleteDeveloperApp.");
      ctx.status(500).json("Internal server error: User email not found.");
      return;
    }
    if (appName == null || appName.isBlank()) {
      System.err.println("Error: appName not found in path parameters.");
      ctx.status(400).json("Bad request: App name missing.");
      return;
    }

    System.out.printf("DELETE /myapps/%s for developer %s\n", appName, devEmail);

    try {
      String path = String.format("/developers/%s/apps/%s", devEmail, appName);
      apigeeFetch(path, "DELETE", null);
      System.out.printf("Successfully deleted app %s for developer %s\n", appName, devEmail);
      ctx.status(204);
    } catch (Exception e) {
      // Handle potential errors, e.g., app not found (404 from Apigee), permission issues
      System.err.printf(
          "Error deleting app %s for developer %s: %s\n", appName, devEmail, e.getMessage());
      // TODO: Check if the error is due to the app not existing (this might depend on how
      // apigeeFetch handles HTTP errors) and return 404 if appropriate.
      // For now, return a generic 500.
      ctx.status(500).json(Map.of("error", "Failed to delete app: " + e.getMessage()));
    }
  }

  /**
   * Parses a timespan string (e.g., "30d", "12w", "1y", "6h") into seconds. Supports days (d),
   * weeks (w), hours (h), and years. Returns -1 if the input string is "-1" (indicating no limit)
   * or cannot be parsed.
   *
   * @param timespan The timespan string to parse.
   * @return The equivalent number of seconds, or -1 for no limit or parse error.
   * @throws IllegalArgumentException if the format is invalid.
   */
  private static long parseTimespanToSeconds(String timespan) {
    if (timespan == null || timespan.isBlank()) {
      throw new IllegalArgumentException("Timespan string cannot be null or empty.");
    }
    timespan = timespan.trim().toLowerCase();

    if ("-1".equals(timespan)) {
      return -1L; // Explicitly handle "-1" as no limit
    }

    // Regex to capture the number and the unit (d, w, m, y)
    Pattern pattern = Pattern.compile("^(\\d+)([hdwy])$");
    Matcher matcher = pattern.matcher(timespan);

    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Invalid timespan format. Expected format like '8h', '30d', '12w', '1y', or '-1'. Found: "
              + timespan);
    }

    long value = Long.parseLong(matcher.group(1));
    String unit = matcher.group(2);

    switch (unit) {
      case "h":
        return TimeUnit.HOURS.toSeconds(value);
      case "d":
        return TimeUnit.DAYS.toSeconds(value);
      case "w":
        return TimeUnit.DAYS.toSeconds(value * 7);
      case "y":
        return Math.round(value * AppUtils.daysInTheComingYear() * 24 * 60 * 60);
      default:
        // Regex check prevents this, but belt and suspenders.
        throw new IllegalArgumentException("Unknown time unit: " + unit);
    }
  }
}
