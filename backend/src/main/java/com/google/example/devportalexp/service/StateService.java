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

package com.google.example.devportalexp.service;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class StateService {
  private static StateService instance;
  private Map<String, Object> settings;

  private static final Map<String, String> environmentVariables =
      Map.of("APIGEE_PROJECT", "project");

  private static InputStream getResourceAsStream(String resourceName) {
    // forcibly prepend a slash. not sure if necessary.
    if (!resourceName.startsWith("/")) {
      resourceName = "/" + resourceName;
    }
    if (!resourceName.startsWith("/resources")) {
      resourceName = "/resources" + resourceName;
    }
    System.out.printf("getResourceAsStream %s\n", resourceName);
    InputStream in = StateService.class.getResourceAsStream(resourceName);
    return in;
  }

  public static StateService getInstance() {
    if (instance == null) {
      instance = new StateService();
    }
    return instance;
  }

  private StateService() {
    // Rely on values provided in environment variables, fall back to the config
    // file if the needed values are missing.

    // First, read from the config file.
    try {
      Type mapType = new TypeToken<HashMap<String, Object>>() {}.getType();
      @SuppressWarnings("unchecked")
      Map<String, Object> castable =
          (Map<String, Object>)
              new GsonBuilder()
                  .setPrettyPrinting()
                  .create()
                  .fromJson(
                      new String(
                          getResourceAsStream("conf/settings.json").readAllBytes(),
                          StandardCharsets.UTF_8),
                      mapType);
      settings = castable;

    } catch (java.lang.Exception exc1) {
      System.out.println("Exception:" + exc1.toString());
      exc1.printStackTrace();
      throw new RuntimeException("uncaught exception", exc1);
    }

    // Override anything in the hard-coded settings with
    // corresponding environment variables.
    environmentVariables.entrySet().stream()
        .forEach(
            entry -> {
              String envVarName = entry.getKey();
              String envVarValue = System.getenv(envVarName);
              if (envVarValue != null && !envVarValue.isEmpty()) {
                settings.put(entry.getValue(), envVarValue);
              }
            });
  }

  public static boolean isRunningInCloud() {
    // Google Cloud Run automatically sets the K_SERVICE environment variable.
    String kService = System.getenv("K_SERVICE");
    return kService != null && !kService.isEmpty();
  }

  public Map<String, Object> getSettings() {
    return settings;
  }
}
