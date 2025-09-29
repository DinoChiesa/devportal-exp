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
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
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
  public static final int MAX_CERTIFICATES = 6;
  public static final int MAX_DEVELOPER_APPS = 10;
  private static final int MAX_API_PRODUCTS_PER_APP = 5;
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
      log.info("Running in Cloud Run, fetching token from metadata server...");
      String metadataUrl = "/computeMetadata/v1/instance/service-accounts/default/token";
      try {
        String responseBody =
            fetch(
                "http",
                "GET",
                "metadata.google.internal",
                metadataUrl,
                Map.of("Metadata-Flavor", "Google"),
                null);
        if (responseBody != null) {
          Map<String, Object> tokenResponse = gson.fromJson(responseBody, mapType);
          if (tokenResponse != null && tokenResponse.containsKey("access_token")) {
            String accessToken = (String) tokenResponse.get("access_token");
            log.info("Successfully fetched token from metadata server.");
            return accessToken;
          }
        }
      } catch (Exception e) {
        log.error(
            "Unexpected error fetching/parsing token from metadata server: {}", e.getMessage(), e);
      }
      return null;
    }
    log.info("Not running in Cloud Run, using gcloud for token...");
    return executeCommand(
        "gcloud",
        "auth",
        "print-access-token",
        "--project",
        (String) appSettings.get("project"),
        "--quiet");
  }

  private static String fetch(
      String method,
      String host,
      String uriPath,
      Map<String, String> requestHeaders,
      Map<String, Object> payload)
      throws URISyntaxException, IOException, InterruptedException {
    return fetch("https", method, host, uriPath, requestHeaders, payload);
  }

  private static String fetch(
      String scheme,
      String method,
      String host,
      String pathAndQuery,
      Map<String, String> requestHeaders,
      Map<String, Object> payload)
      throws URISyntaxException, IOException, InterruptedException {
    final String userInfo = null;
    final int port = -1;
    String uriPath = pathAndQuery;
    String query = "";

    int ix = pathAndQuery.indexOf('?');
    if (ix != -1) {
      uriPath = pathAndQuery.substring(0, ix);
      query = pathAndQuery.substring(ix + 1);
    }

    // NB: use the 7-param URI ctor to get proper % encoding of the path
    // segments containing spaces.
    URI uri = new URI(scheme, userInfo, host, port, uriPath, query, null);
    log.debug("*** fetch uri {}", uri.toString());

    HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uri);
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
    log.debug("Response headers:\n{}", responseHeaders.toString());
    String body = response.body();
    log.debug("\n\n=>\n{}", body);
    return body;
  }

  private Map<String, Object> apigeeFetch(
      String pathFragment, String method, Map<String, Object> payload)
      throws URISyntaxException, IOException, InterruptedException {
    String apigeeProject = (String) appSettings.get("project");
    String uriPath = String.format("/v1/organizations/%s%s", apigeeProject, pathFragment);
    String apigeeOrgToken = (String) CacheService.getInstance().get("apigeetoken");
    String stringResult =
        fetch(
            method,
            "apigee.googleapis.com",
            uriPath,
            Map.of("Authorization", "Bearer " + apigeeOrgToken),
            payload);
    Map<String, Object> json = gson.fromJson(stringResult, mapType);
    return json;
  }

  private Map<String, Object> apigeeGet(String path)
      throws URISyntaxException, IOException, InterruptedException {
    return apigeeFetch(path, "GET", null);
  }

  private Map<String, Object> apigeePost(String partialPath, Map<String, Object> payload)
      throws URISyntaxException, IOException, InterruptedException {
    return apigeeFetch(partialPath, "POST", payload);
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
      log.info("Executing command: {}", String.join(" ", command)); // Log the command being run

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
              log.error("Exception in slurp: {}", exc1.toString(), exc1);
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
      log.error("Exception executing command: {}", exc1.toString(), exc1);
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
      log.error("Exception loading products: {}", exc1.toString(), exc1);
      throw new RuntimeException("uncaught exception", exc1);
    }
  }

  /** GET /api/apiproducts */
  public void getAllApiProducts(final Context ctx) {
    log.info("GET /api/apiproducts");
    @SuppressWarnings("unchecked")
    List<ApiProduct> apiProducts = (List<ApiProduct>) CacheService.getInstance().get("apiproducts");
    ctx.json((apiProducts != null) ? apiProducts : Collections.emptyList());
  }

  /** GET /api/me/apps */
  public void getDeveloperApps(final Context ctx)
      throws IOException, InterruptedException, URISyntaxException {
    // Session is valid, add user info to context if needed
    log.info("GET /api/me/apps");
    String devEmail = ctx.attribute("userEmail");
    Map<String, Object> devResponse = apigeeGet("/developers/" + devEmail);
    @SuppressWarnings("unchecked")
    List<String> appList = (List<String>) devResponse.get("apps");
    ctx.json((appList != null) ? appList : Collections.emptyList());
  }

  /** GET /api/me/apps/{appname} */
  public void getDeveloperAppDetails(final Context ctx)
      throws IOException, InterruptedException, URISyntaxException {
    String appName = ctx.pathParam("appname");
    String devEmail = ctx.attribute("userEmail");

    if (devEmail == null || devEmail.isBlank()) {
      // This shouldn't happen if the before filter is working correctly
      log.error("userEmail not found in context for getDeveloperApp.");
      ctx.status(500).json("Internal server error: User email not found.");
      return;
    }
    log.info("Fetching app details for userEmail {} app {}", devEmail, appName);
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

    log.info("Processing {} API products for key expiry...", apiProducts.size());
    long minExpirySeconds = -1L; // -1 indicates no limit found yet or infinite

    for (String productName : apiProducts) {
      try {
        log.info("Fetching details for API product: {}", productName);
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
            log.info(
                "Found 'max-key-lifetime' attribute for product {}: {}", productName, timespanStr);
            try {
              long currentProductExpirySeconds = parseTimespanToSeconds(timespanStr);
              log.info(
                  "Parsed expiry for {} to {} seconds", productName, currentProductExpirySeconds);

              if (currentProductExpirySeconds != -1) { // If the current product has a limit
                if (minExpirySeconds == -1
                    || currentProductExpirySeconds < minExpirySeconds) { // If it's the first limit
                  // found or smaller than
                  // current min
                  minExpirySeconds = currentProductExpirySeconds;
                  log.info("New minimum expiry set to {} seconds", minExpirySeconds);
                }
              }
              // If currentProductExpirySeconds is -1 (no limit for this product), it doesn't
              // affect the minimum unless minExpirySeconds is still -1.
            } catch (IllegalArgumentException e) {
              log.warn(
                  "Could not parse 'max-key-lifetime' value '{}' for product {}: {}",
                  timespanStr,
                  productName,
                  e.getMessage());
              // Currently ignoring parse errors and proceeding.
            }
          } else {
            log.info(
                "No 'max-key-lifetime' attribute found for product {}. Assuming no limit.",
                productName);
          }
        } else {
          log.info("No attributes found for product {}. Assuming no limit.", productName);
        }
      } catch (Exception e) {
        log.error(
            "Error fetching or processing details for API product {}: {}",
            productName,
            e.getMessage(),
            e);
        // Fail fast if product details cannot be fetched/processed
        // Return empty optional to signal error to the caller.
        return Optional.empty();
      }
    }
    return Optional.of(minExpirySeconds); // Return calculated minimum expiry
  }

  // --- Helper methods for createDeveloperApp ---

  private Map<String, Object> getValidPayload(Context ctx) {
    String contentType = ctx.contentType();
    if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
      log.warn("Invalid Content-Type: {}", contentType);
      ctx.status(415).json(Map.of("error", "Request must be application/json"));
      return null;
    }

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> payloadMap = (Map<String, Object>) ctx.bodyAsClass(Map.class);
      return payloadMap;
    } catch (Exception e) {
      log.warn("Error parsing create app request body or extracting fields: {}", e.getMessage(), e);
      ctx.status(400).json(Map.of("error", "Invalid JSON payload or structure"));
      return null;
    }
  }

  private boolean checkAppLimit(Context ctx, String devEmail)
      throws IOException, InterruptedException, URISyntaxException {
    try {
      String appsUri = String.format("/developers/%s/apps", devEmail);
      Map<String, Object> currentAppsResponse = apigeeGet(appsUri);
      @SuppressWarnings("unchecked")
      List<String> currentAppList = (List<String>) currentAppsResponse.get("app");
      int appCount = (currentAppList != null) ? currentAppList.size() : 0;

      if (appCount >= MAX_DEVELOPER_APPS) {
        log.warn(
            "Developer {} already has {} apps (limit is {}).",
            devEmail,
            appCount,
            MAX_DEVELOPER_APPS);
        ctx.status(400)
            .json(
                Map.of(
                    "error",
                    String.format(
                        "Maximum number of developer apps (%d) already registered.",
                        MAX_DEVELOPER_APPS)));
        return false;
      }
      log.info("Developer {} has {} apps, proceeding with app creation.", devEmail, appCount);
      return true;
    } catch (IOException | InterruptedException | URISyntaxException e) {
      // Re-throw exceptions related to the Apigee call to be handled by the main method's catch-all
      throw e;
    } catch (Exception e) {
      // Handle other potential errors during the app count check
      log.error("Error checking app count for developer {}: {}", devEmail, e.getMessage(), e);
      ctx.status(500).json(Map.of("error", "Failed to verify current app count."));
      return false;
    }
  }

  private Optional<List<String>> getValidApiProducts(Context ctx, Map<String, Object> payloadMap) {
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

    // Validate presence and count
    if (apiProducts == null || apiProducts.isEmpty()) {
      log.warn("'apiProducts' field is missing, empty, or not a list of strings.");
      ctx.status(400).json(Map.of("error", "Request must include a non-empty 'apiProducts' list."));
      return Optional.empty();
    }

    if (apiProducts.size() > MAX_API_PRODUCTS_PER_APP) {
      log.warn(
          "Too many API products requested ({}). Maximum allowed is {}.",
          apiProducts.size(),
          MAX_API_PRODUCTS_PER_APP);
      ctx.status(400)
          .json(
              Map.of(
                  "error",
                  String.format(
                      "Too many API products selected (%d). Maximum allowed is %d.",
                      apiProducts.size(), MAX_API_PRODUCTS_PER_APP)));
      return Optional.empty();
    }
    return Optional.of(apiProducts);
  }

  private boolean prepareAppCreationPayload(
      Context ctx, Map<String, Object> payloadMap, List<String> apiProducts)
      throws IOException, InterruptedException, URISyntaxException {
    // Calculate minimum key expiry based on selected API products
    Optional<Long> minExpiryOptional = calculateMinimumKeyExpirySeconds(apiProducts);
    if (minExpiryOptional.isEmpty()) {
      // The cause will have been logged in the calculateMinimumKeyExpirySeconds helper method.
      // We return 500 here as the overall operation failed.
      ctx.status(500)
          .json(Map.of("error", "Failed to process API product details for key expiry."));
      return false;
    }

    // Add keyExpiresIn to the payload if a minimum finite expiry was found
    long minExpirySeconds = minExpiryOptional.get();
    if (minExpirySeconds != -1) {
      log.info(
          "Setting 'keyExpiresIn' in request payload to {} milliseconds.", minExpirySeconds * 1000);
      // Apigee expects keyExpiresIn in milliseconds
      payloadMap.put("keyExpiresIn", String.valueOf(minExpirySeconds * 1000));
    }

    // Ensure standard attributes are present
    payloadMap.putIfAbsent(
        "attributes", List.of(Map.of("name", "createdBy", "value", "devportal-exp")));
    return true;
  }

  // --- End Helper methods for createDeveloperApp ---

  /** POST /api/me/apps */
  public void createDeveloperApp(final Context ctx)
      throws IOException, InterruptedException, URISyntaxException {
    String devEmail = ctx.attribute("userEmail");
    if (devEmail == null || devEmail.isBlank()) {
      log.error("userEmail not found in context.");
      ctx.status(500).json("Internal server error: User email not found.");
      return;
    }

    Map<String, Object> payloadMap = getValidPayload(ctx);
    if (payloadMap == null) {
      return; // Error already handled by helper
    }

    if (!checkAppLimit(ctx, devEmail)) {
      return; // Error already handled by helper
    }

    Optional<List<String>> apiProductsOptional = getValidApiProducts(ctx, payloadMap);
    if (apiProductsOptional.isEmpty()) {
      return; // Error already handled by helper
    }
    List<String> apiProducts = apiProductsOptional.get();

    if (!prepareAppCreationPayload(ctx, payloadMap, apiProducts)) {
      return; // Error already handled by helper
    }

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
      log.warn(String.format("Exception while getting details for %s", devEmail), e);
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
      log.error("User details (email, firstName, lastName) not found in context.");
      ctx.status(500).json("Internal server error: User details not found in session.");
      return;
    }

    // Generate unique username
    String userName =
        String.format(
            "%s-%s-%04d",
            firstName.toLowerCase(), lastName.toLowerCase(), new java.util.Random().nextInt(10000));

    log.info(
        "Creating developer: email={}, firstName={}, lastName={}, userName={}",
        devEmail,
        firstName,
        lastName,
        userName);

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
      log.error("Error creating new developer", e);
      ctx.status(500).json(Map.of("error", "unhandled error"));
      return;
    }
  }

  // --- Helper methods for registerCertificate ---

  private record ProcessedCertificate(X509Certificate certificate, String pem) {}

  private Optional<Map<String, Object>> parseAndValidateRegisterCertificateRequest(Context ctx) {
    String contentType = ctx.contentType();
    if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
      log.warn("Invalid Content-Type for registerCertificate: {}", contentType);
      ctx.status(415).json(Map.of("error", "Request must be application/json"));
      return Optional.empty();
    }

    Map<String, Object> payload;
    try {
      payload = gson.fromJson(ctx.body(), mapType);
    } catch (Exception e) {
      log.warn("Failed to parse JSON payload for registerCertificate", e);
      ctx.status(400).json(Map.of("error", "Payload cannot be parsed"));
      return Optional.empty();
    }

    if (payload == null) { // Should be caught by try-catch, but as a safeguard
      log.warn("Parsed JSON payload is null for registerCertificate");
      ctx.status(400).json(Map.of("error", "Payload cannot be parsed"));
      return Optional.empty();
    }

    boolean hasPublicKey = payload.containsKey("publicKey");
    boolean hasCertificate = payload.containsKey("certificate");

    // Validate payload structure:
    // 1. If publicKey is present, keyId must also be present.
    // 2. publicKey and certificate are mutually exclusive.
    boolean invalidPayload =
        (hasPublicKey && !payload.containsKey("keyId")) || (hasPublicKey == hasCertificate);

    if (invalidPayload) {
      log.warn("Invalid payload structure for registerCertificate: {}", payload);
      ctx.status(400)
          .json(Map.of("error", "Invalid JSON payload: missing or inconsistent properties."));
      return Optional.empty();
    }
    log.info("registerCertificate payload validation successful.");
    return Optional.of(payload);
  }

  private Optional<List<Map<String, Object>>> checkCertificateLimitAndGetAttributes(
      Context ctx, String devEmail) throws IOException, InterruptedException, URISyntaxException {
    String attributesUri = String.format("/developers/%s/attributes", devEmail);
    Map<String, Object> currentDevAttrsResponse = apigeeGet(attributesUri);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> currentAttrList =
        (List<Map<String, Object>>) currentDevAttrsResponse.get("attribute");

    long certificateCount = 0;
    if (currentAttrList != null) {
      certificateCount =
          currentAttrList.stream()
              .filter(
                  mapEntry -> {
                    if (mapEntry == null) return false;
                    Object nameValue = mapEntry.get("name");
                    return nameValue instanceof String && ((String) nameValue).startsWith("cert-");
                  })
              .count();
    }

    if (certificateCount >= MAX_CERTIFICATES) {
      log.warn(
          "Developer {} already has {} certificates (limit is {}).",
          devEmail,
          certificateCount,
          MAX_CERTIFICATES);
      ctx.status(400)
          .json(
              Map.of(
                  "error",
                  String.format(
                      "Maximum number of certificates (%d) already registered.",
                      MAX_CERTIFICATES)));
      return Optional.empty();
    }
    log.info(
        "Developer {} has {} certificates, proceeding with registration.",
        devEmail,
        certificateCount);
    return Optional.of(currentAttrList == null ? new ArrayList<>() : currentAttrList);
  }

  private Optional<ProcessedCertificate> generateOrUploadCertificate(
      Context ctx,
      String devEmail,
      String userName, // Full name from session
      Map<String, Object> payload,
      List<Map<String, Object>> currentDevAttrs) {
    try {
      String certificatePem;
      X509Certificate x509Cert;

      if (payload.containsKey("publicKey")) {
        // Generate a certificate from an uploaded public key
        String partnerOrgName = "Unknown Partner Org"; // Default
        if (currentDevAttrs != null) {
          partnerOrgName =
              currentDevAttrs.stream()
                  .filter(
                      attr ->
                          attr != null
                              && "partner-name".equals(attr.get("name"))
                              && attr.get("value") instanceof String)
                  .map(attr -> (String) attr.get("value"))
                  .findFirst()
                  .orElse(partnerOrgName);
        }
        log.info("Using partner organization name for cert generation: {}", partnerOrgName);

        String subjectDN =
            String.format(
                "CN=%s, O=%s, serialNumber=%s",
                userName, // Use the full name for CN
                partnerOrgName,
                (String) payload.get("keyId"));

        PublicKey publicKeyToSign = KeyUtility.decodePublicKey((String) payload.get("publicKey"));
        x509Cert =
            X509CertificateService.getInstance()
                .generateNewSignedCertificate(publicKeyToSign, subjectDN, devEmail, partnerOrgName);
        certificatePem = KeyUtility.toPem(x509Cert);
        log.info("Successfully generated new certificate for dev {}", devEmail);
      } else {
        // User is uploading a previously-generated certificate
        certificatePem = (String) payload.get("certificate");
        x509Cert = KeyUtility.decodeCertificate(certificatePem);
        X509CertificateService.enforceClientCertificateConstraints(x509Cert);
        log.info("Successfully processed uploaded certificate for dev {}", devEmail);
      }
      return Optional.of(new ProcessedCertificate(x509Cert, certificatePem));
    } catch (KeyUtility.KeyParseException | IllegalArgumentException e) {
      log.warn("Error processing/validating certificate/key: {}", e.getMessage());
      ctx.status(400).json(Map.of("error", e.getMessage()));
      return Optional.empty();
    } catch (Exception e) { // Catch broader exceptions from generation/service calls
      log.error("Unexpected error during certificate generation/processing", e);
      ctx.status(500).json(Map.of("error", "Internal server error during certificate processing."));
      return Optional.empty();
    }
  }

  private Optional<String> updateDeveloperAttributesWithCertificate(
      Context ctx,
      String devEmail,
      X509Certificate certificate,
      List<Map<String, Object>> currentAttributes)
      throws IOException, InterruptedException, URISyntaxException {
    try {
      String fingerprint = KeyUtility.fingerprintBase64(certificate);
      // The verifyFingerprintUniqueness method throws IllegalArgumentException if duplicate
      verifyFingerprintUniqueness(fingerprint, currentAttributes);

      String newCertificateIdentifier = String.format("cert-%s", nowAsYyyyMmDdHHmmss());

      // Create a mutable list for attributes if it's not already or make a copy
      List<Map<String, Object>> updatedAttributes = new ArrayList<>(currentAttributes);
      updatedAttributes.add(Map.of("name", newCertificateIdentifier, "value", fingerprint));

      String attributesUri = String.format("/developers/%s/attributes", devEmail);
      apigeePost(attributesUri, Map.of("attribute", updatedAttributes));
      log.info(
          "Successfully updated developer attributes for {} with new certificate ID: {}",
          devEmail,
          newCertificateIdentifier);
      return Optional.of(newCertificateIdentifier);
    } catch (IllegalArgumentException e) { // Specifically for fingerprint uniqueness
      log.warn("Failed to update developer attributes for {}: {}", devEmail, e.getMessage());
      ctx.status(400).json(Map.of("error", e.getMessage()));
      return Optional.empty();
    } catch (Exception e) { // Catch other Apigee call or KeyUtility exceptions
      log.error(
          "Error updating developer attributes for {} with new certificate: {}",
          devEmail,
          e.getMessage(),
          e);
      ctx.status(500)
          .json(Map.of("error", "Failed to update developer attributes with new certificate."));
      return Optional.empty();
    }
  }

  // --- End Helper methods for registerCertificate ---

  /**
   * POST /api/me/certificates
   *
   * <p>Expects a json with these members: publicKey - PEM-encoded public key keyId - arbitrary
   * string identifying the key OR certificate - PEM-encoded certificate
   */
  public void registerCertificate(final Context ctx)
      throws IOException, InterruptedException, URISyntaxException {
    String devEmail = ctx.attribute("userEmail");
    String userName = ctx.attribute("name"); // Full name from session

    if (devEmail == null || devEmail.isBlank() || userName == null || userName.isBlank()) {
      log.warn("User details (email, name) not found in context for registerCertificate.");
      ctx.status(500).json("Internal server error: User details not found in session.");
      return;
    }
    log.info("Attempting to register certificate for developer: {}", devEmail);

    Optional<Map<String, Object>> payloadOptional = parseAndValidateRegisterCertificateRequest(ctx);
    if (payloadOptional.isEmpty()) {
      return; // Error handled in helper
    }
    Map<String, Object> payload = payloadOptional.get();

    Optional<List<Map<String, Object>>> attributesOptional =
        checkCertificateLimitAndGetAttributes(ctx, devEmail);
    if (attributesOptional.isEmpty()) {
      return; // Error handled in helper
    }
    List<Map<String, Object>> currentDevAttrs = attributesOptional.get();

    Optional<ProcessedCertificate> processedCertOptional =
        generateOrUploadCertificate(ctx, devEmail, userName, payload, currentDevAttrs);
    if (processedCertOptional.isEmpty()) {
      return; // Error handled in helper
    }
    ProcessedCertificate processedCert = processedCertOptional.get();

    // Fetch attributes again before updating to minimize race conditions,
    // though a small window still exists. For higher consistency, a more
    // complex locking or conditional update mechanism via Apigee would be needed.
    // For this example, we'll re-fetch.
    String attributesUri = String.format("/developers/%s/attributes", devEmail);
    Map<String, Object> freshDevAttrsResponse = apigeeGet(attributesUri);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> freshDevAttrs =
        (List<Map<String, Object>>) freshDevAttrsResponse.get("attribute");
    if (freshDevAttrs == null) {
      freshDevAttrs = new ArrayList<>();
    }

    Optional<String> newCertIdOptional =
        updateDeveloperAttributesWithCertificate(
            ctx, devEmail, processedCert.certificate(), freshDevAttrs);
    if (newCertIdOptional.isEmpty()) {
      return; // Error handled in helper
    }
    String newCertificateIdentifier = newCertIdOptional.get();

    try {
      Map<String, Object> response =
          Map.of(
              "pem",
              processedCert.pem(),
              "fingerprint",
              KeyUtility.fingerprintBase64(processedCert.certificate()),
              "certificate-id",
              newCertificateIdentifier,
              "subjectDN",
              processedCert.certificate().getSubjectX500Principal().toString(),
              "notBefore",
              certDate(processedCert.certificate().getNotBefore()),
              "notAfter",
              certDate(processedCert.certificate().getNotAfter()));
      ctx.status(200).json(response);
    } catch (NoSuchProviderException
        | NoSuchAlgorithmException
        | CertificateEncodingException exc1) {
      log.error(
          "Exception during certificate registration finalization: {}", exc1.toString(), exc1);
      ctx.status(500).json("Internal server error hwile registering a certificate.");
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
      log.error("userEmail not found in context.");
      ctx.status(500).json("Internal server error: User email not found.");
      return;
    }
    String certId = ctx.pathParam("certId");
    log.info("deregisterCertificate [{} {}]...", devEmail, certId);

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
      log.error("Error deregistering certificate", e);
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

  /** DELETE /api/me/apps/{appname} */
  public void deleteDeveloperApp(final Context ctx)
      throws IOException, InterruptedException, URISyntaxException {
    String appName = ctx.pathParam("appname");
    String devEmail = ctx.attribute("userEmail");

    if (devEmail == null || devEmail.isBlank()) {
      log.error("userEmail not found in context for deleteDeveloperApp.");
      ctx.status(500).json("Internal server error: User email not found.");
      return;
    }
    if (appName == null || appName.isBlank()) {
      log.error("appName not found in path parameters.");
      ctx.status(400).json("Bad request: App name missing.");
      return;
    }

    log.info("DELETE /api/me/apps/{} for developer {}", appName, devEmail);

    try {
      String path = String.format("/developers/%s/apps/%s", devEmail, appName);
      apigeeFetch(path, "DELETE", null);
      log.info("Successfully deleted app {} for developer {}", appName, devEmail);
      ctx.status(204);
    } catch (Exception e) {
      // Handle potential errors, e.g., app not found (404 from Apigee), permission issues
      log.error("Error deleting app {} for developer {}: {}", appName, devEmail, e.getMessage(), e);
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
