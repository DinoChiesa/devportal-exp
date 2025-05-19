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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.function.Function;
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

    // Normalize the pattern to use "/"
    String normalizedPattern = globPattern.replace("\\", "/");

    if (!normalizedPattern.startsWith("resources/")) {
      normalizedPattern = "resources/" + normalizedPattern;
    }
    // Extract base path and file name pattern
    int lastSlash = normalizedPattern.lastIndexOf('/');
    String basePath = (lastSlash > -1) ? normalizedPattern.substring(0, lastSlash) : "";
    String fileNamePatternOnly =
        (lastSlash > -1) ? normalizedPattern.substring(lastSlash + 1) : normalizedPattern;

    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + fileNamePatternOnly);

    URL resourceUrl = classLoader.getResource(basePath);
    if (resourceUrl == null) {
      // Base path not found
      return null;
    }

    // Define a function to process the stream of candidates:
    // sort, pick the last, and map to full path.
    Function<Stream<String>, String> processCandidates =
        (candidateStream) ->
            candidateStream
                .sorted(Comparator.naturalOrder())
                .reduce((first, second) -> second) // Selects the last element
                .map(name -> basePath.isEmpty() ? name : basePath + "/" + name) // Adjust mapping
                .orElse(null);

    try {
      if ("file".equals(resourceUrl.getProtocol())) {
        Path dirPath = Paths.get(resourceUrl.toURI());
        if (Files.isDirectory(dirPath)) {
          try (Stream<Path> stream = Files.list(dirPath)) {
            Stream<String> candidates =
                stream
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName) // Get only the file name part for matching
                    .filter(matcher::matches)
                    .map(path -> path.toString());
            return processCandidates.apply(candidates);
          }
        }
      } else if ("jar".equals(resourceUrl.getProtocol())) {
        JarURLConnection jarURLConnection = (JarURLConnection) resourceUrl.openConnection();
        try (JarFile jarFile = jarURLConnection.getJarFile()) {
          String searchPrefixInJar = basePath.isEmpty() ? "" : basePath + "/";
          // For JARs, the entry name is already the full path relative to JAR root
          Stream<String> candidates =
              jarFile.stream()
                  .filter(
                      entry ->
                          entry.getName().startsWith(searchPrefixInJar) && !entry.isDirectory())
                  .filter(
                      entry -> {
                        String nameInDir = entry.getName().substring(searchPrefixInJar.length());
                        // Ensure it's a direct child, not in a sub-subdirectory of basePath
                        return !nameInDir.contains("/") && matcher.matches(Paths.get(nameInDir));
                      })
                  .map(JarEntry::getName);
          return processCandidates.apply(candidates);
        }
      }
      return null; // No matching protocol or conditions met
    } catch (URISyntaxException e) {
      throw new IOException("Error converting URL to URI: " + resourceUrl, e);
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
}
