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

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

public class CacheService {
  private static CacheService instance;

  private final LoadingCache<String, Object> cache;

  // Each loader has a test function that looks at the key. If the test
  // returns true, then that loader is used.
  private final Map<Predicate<String>, Function<String, Object>> loaders = new HashMap<>();

  public static CacheService getInstance() {
    if (instance == null) {
      instance = new CacheService();
    }
    return instance;
  }

  private CacheService() {
    CacheLoader<String, Object> cacheLoader =
        key -> {
          Optional<Object> result =
              loaders.entrySet().stream()
                  .filter(entry -> entry.getKey().test(key))
                  .findFirst()
                  .map(
                      entry -> {
                        System.out.printf("--- CacheLoader: Loading data for key: %s ---\n", key);
                        return entry.getValue().apply(key);
                      });

          return result.orElse(null);
        };

    // Build the LoadingCache instance
    this.cache =
        Caffeine.newBuilder()
            // Evict entries 3 minutes after they were last written (created or updated)
            .expireAfterWrite(3, TimeUnit.MINUTES)
            // Optionally, set a maximum size
            .maximumSize(500)
            // Build the cache with the loader defined above
            .build(cacheLoader);
  }

  public Object get(final String key) {
    return cache.get(key);
  }

  public CacheService registerLoader(
      final Predicate<String> test, final Function<String, Object> loader)
      throws IllegalStateException {
    loaders.put(test, loader);
    return this;
  }
}
