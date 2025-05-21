// Copyright © 2024-2025 Google, LLC.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// All rights reserved.

package com.google.example.devportalexp;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class AppUtils {

  /**
   * Finds the name of a resource within the classpath that matches the given glob pattern.
   *
   * @param globPattern The glob pattern (e.g., "keys/certificate-*.pem").
   * @return The full name of the first matching resource (e.g., "keys/certificate-123.pem"), or
   *     null if not found.
   * @throws IOException If an I/O error occurs.
   */
  public static String findResourceNameByPattern(String globPattern) throws IOException {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    if (classLoader == null) {
      classLoader = AppUtils.class.getClassLoader();
    }

    String normalizedPattern = globPattern.replace("\\", "/");
    if (!normalizedPattern.startsWith("resources/")) {
      normalizedPattern = "resources/" + normalizedPattern;
    }

    int lastSlash = normalizedPattern.lastIndexOf('/');
    String basePath = (lastSlash > -1) ? normalizedPattern.substring(0, lastSlash) : "";
    String fileNamePatternOnly =
        (lastSlash > -1) ? normalizedPattern.substring(lastSlash + 1) : normalizedPattern;

    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + fileNamePatternOnly);
    URL resourceUrl = classLoader.getResource(basePath);

    if (resourceUrl == null) {
      return null;
    }

    try {
      if ("file".equals(resourceUrl.getProtocol())) {
        return findMatchingResourceInDirectory(resourceUrl, basePath, matcher);
      } else if ("jar".equals(resourceUrl.getProtocol())) {
        return findMatchingResourceInJar(resourceUrl, basePath, matcher);
      }
      return null; // Protocol not supported or no conditions met
    } catch (URISyntaxException e) {
      throw new IOException("Error converting URL to URI: " + resourceUrl, e);
    }
  }

  private static String processCandidateStream(Stream<String> candidateStream, String basePath) {
    return candidateStream
        .sorted(Comparator.naturalOrder())
        .reduce((first, second) -> second) // Selects the last element (latest by name)
        .map(
            name ->
                (basePath.isEmpty() || name.startsWith(basePath)) ? name : basePath + "/" + name)
        .orElse(null);
  }

  private static String findMatchingResourceInDirectory(
      URL directoryUrl, String basePath, PathMatcher matcher)
      throws URISyntaxException, IOException {
    Path dirPath = Paths.get(directoryUrl.toURI());
    if (Files.isDirectory(dirPath)) {
      try (Stream<Path> stream = Files.list(dirPath)) {
        Stream<String> candidates =
            stream
                .filter(Files::isRegularFile)
                .map(Path::getFileName) // Get only the file name part for matching
                .filter(matcher::matches)
                .map(Path::toString); // Convert matched Path (filename only) to String
        return processCandidateStream(candidates, basePath);
      }
    }
    return null;
  }

  private static String findMatchingResourceInJar(
      URL jarResourceUrl, String basePathInJar, PathMatcher matcher) throws IOException {
    JarURLConnection jarURLConnection = (JarURLConnection) jarResourceUrl.openConnection();
    try (JarFile jarFile = jarURLConnection.getJarFile()) {
      // Ensure basePathInJar ends with a slash if it's not empty, for correct prefix matching.
      String searchPrefixInJar =
          basePathInJar.isEmpty() ? "" : (basePathInJar.endsWith("/") ? basePathInJar : basePathInJar + "/");

      Stream<String> candidates =
          jarFile.stream()
              .filter(entry -> !entry.isDirectory() && entry.getName().startsWith(searchPrefixInJar))
              .map(JarEntry::getName) // Full path within JAR
              .filter(
                  entryName -> {
                    // Extract the simple file name part from the entry name
                    String fileNameOnly = entryName.substring(searchPrefixInJar.length());
                    // Ensure it's a direct child and matches the pattern
                    return !fileNameOnly.contains("/") && matcher.matches(Paths.get(fileNameOnly));
                  });
      // For JARs, processCandidateStream gets the full path, so basePath is effectively empty
      // as the full path is already what we want.
      return processCandidateStream(candidates, "");
    }
  }

  /**
   * Return the number of days from the current date to the same date next year. Eg, when today is
   * June 1st, the result will be 366 if NEXT year is a leap year, and 365 otherwise.
   *
   * <p>For the edge case: If the current date is Feb 29th, `today.plusYears(1)` will result in Feb
   * 28th of the next year, and the duration will correctly be 365 days.
   *
   * @return the number of days from the current date to the same date next year.
   */
  public static int daysInTheComingYear() {
    LocalDate today = LocalDate.now();
    LocalDate nextYearSameDay = today.plusYears(1);
    return (int) ChronoUnit.DAYS.between(today, nextYearSameDay);
  }

  /**
   * Retrieves a resource stream from the classpath. Ensures the path starts with "/resources/" if
   * not already, and always prepends a leading slash if missing.
   *
   * @param resourceName The name of the resource.
   * @return An InputStream for the resource, or null if not found.
   */
  public static java.io.InputStream getResourceAsStream(String resourceName) {
    // forcibly prepend a slash. not sure if necessary.
    if (!resourceName.startsWith("/")) {
      resourceName = "/" + resourceName;
    }
    if (!resourceName.startsWith("/resources")) {
      resourceName = "/resources" + resourceName;
    }
    // Using System.out here for now to match existing pattern in App.java/StateService.java
    // Consider replacing with SLF4J logger if standardizing logging.
    System.out.printf("AppUtils.getResourceAsStream %s\n", resourceName);
    // Use a class known to be in the same classloader context, e.g., AppUtils itself.
    return AppUtils.class.getResourceAsStream(resourceName);
  }
}
