#!/bin/bash
# Copyright 2024-2025 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

check_shell_variables() {
  local MISSING_ENV_VARS
  MISSING_ENV_VARS=()
  for var_name in "$@"; do
    if [[ -z "${!var_name}" ]]; then
      MISSING_ENV_VARS+=("$var_name")
    fi
  done

  [[ ${#MISSING_ENV_VARS[@]} -ne 0 ]] && {
    printf -v joined '%s,' "${MISSING_ENV_VARS[@]}"
    printf "You must set these environment variables: %s\n" "${joined%,}"
    exit 1
  }

  printf "Settings in use:\n"
  for var_name in "${env_vars_to_check[@]}"; do
    printf "  %s=%s\n" "$var_name" "${!var_name}"
  done
}

