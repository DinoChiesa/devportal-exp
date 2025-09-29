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

import com.google.example.devportalexp.AppUtils;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class StateService {
  private static StateService instance;
  private Map<String, Object> settings;
  private Map<String, String> buildInfo;

  private static final Map<String, String> environmentVariables =
      Map.of("APIGEE_PROJECT", "project");

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
                          AppUtils.getResourceAsStream("conf/settings.json").readAllBytes(),
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

    // Load build info from git.properties
    this.buildInfo = new HashMap<>();
    try (InputStream input = StateService.class.getResourceAsStream("/git.properties")) {
      Properties props = new Properties();
      if (input == null) {
        System.out.println("WARNING: git.properties not found. Build info will be unavailable.");
        this.buildInfo.put("commit", "dev");
        this.buildInfo.put("buildTime", "now");
      } else {
        props.load(input);
        this.buildInfo.put("commit", props.getProperty("git.commit.id.abbrev", "unknown"));
        this.buildInfo.put("buildTime", props.getProperty("git.build.time", "unknown"));
      }
    } catch (IOException ex) {
      System.out.println("ERROR: Failed to load git.properties.");
      ex.printStackTrace();
      this.buildInfo.put("commit", "error");
      this.buildInfo.put("buildTime", "error");
    }
  }

  public Map<String, String> getBuildInfo() {
    return this.buildInfo;
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
