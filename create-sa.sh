#!/usr/bin/bash
# -*- mode: shell-script; sh-shell: bash; coding: utf-8 -*-

# Copyright 2023-2025 Google LLC
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

SA_REQUIRED_ROLES=("roles/apigee.developerAdmin")

env_vars_to_check=(
  "CLOUDRUN_PROJECT"
  "SERVICE_ACCOUNT"
  "APIGEE_PROJECT"
)

source ./lib/utils.sh

check_and_maybe_create_sa() {
  local ROLE AVAILABLE_ROLES
  printf "Checking Service account (%s)...\n" "${FULL_SA_EMAIL}"
  if gcloud iam service-accounts describe "${FULL_SA_EMAIL}" --project="$CLOUDRUN_PROJECT" --quiet 2>&1; then
    printf "That service account already exists.\n"
    printf "Checking for required roles....\n"
    printf "  Checking project %s....\n" "$APIGEE_PROJECT"

    # shellcheck disable=SC2076
    AVAILABLE_ROLES=($(gcloud projects get-iam-policy "${APIGEE_PROJECT}" \
      --flatten="bindings[].members" \
      --filter="bindings.members:${FULL_SA_EMAIL}" |
      grep -v deleted | grep -A 1 members | grep role | sed -e 's/role: //'))

    for j in "${!SA_REQUIRED_ROLES[@]}"; do
      ROLE=${SA_REQUIRED_ROLES[j]}
      printf "    check the role %s...\n" "$ROLE"
      if ! [[ ${AVAILABLE_ROLES[*]} =~ "${ROLE}" ]]; then
        printf "Adding role %s...\n" "${ROLE}"
        echo "gcloud projects add-iam-policy-binding ${APIGEE_PROJECT} \
                 --condition=None \
                 --member=serviceAccount:${FULL_SA_EMAIL} \
                 --role=${ROLE}"
        if gcloud projects add-iam-policy-binding "${APIGEE_PROJECT}" \
          --condition=None \
          --member="serviceAccount:${FULL_SA_EMAIL}" \
          --role="${ROLE}" --quiet 2>&1; then
          printf "Success\n"
        else
          printf "\n*** FAILED\n\n"
          printf "You must manually run:\n\n"
          echo "gcloud projects add-iam-policy-binding ${APIGEE_PROJECT} \
                 --condition=None \
                 --member=serviceAccount:${FULL_SA_EMAIL} \
                 --role=${ROLE}"
        fi
      else
        printf "      That role is already set.\n"
      fi
    done

  else
    printf "Creating Service account (%s)...\n" "${FULL_SA_EMAIL}"
    echo "gcloud iam service-accounts create $SERVICE_ACCOUNT --project=$CLOUDRUN_PROJECT --quiet"
    gcloud iam service-accounts create "$SERVICE_ACCOUNT" --project="$CLOUDRUN_PROJECT" --quiet 2>&1
    printf "There can be errors if all these changes happen too quickly, so we need to sleep a bit...\n"
    sleep 12

    printf "Granting access for that service account to project %s...\n" "$APIGEE_PROJECT"
    for j in "${!SA_REQUIRED_ROLES[@]}"; do
      ROLE=${SA_REQUIRED_ROLES[j]}
      printf "  Adding role %s...\n" "${ROLE}"
      echo "gcloud projects add-iam-policy-binding ${APIGEE_PROJECT} \
               --condition=None \
               --member=serviceAccount:${FULL_SA_EMAIL} \
               --role=${ROLE} --quiet"
      if gcloud projects add-iam-policy-binding "${APIGEE_PROJECT}" \
        --condition=None \
        --member="serviceAccount:${FULL_SA_EMAIL}" \
        --role="${ROLE}" --quiet 2>&1; then
        printf "Success\n"
      else
        printf "\n*** FAILED\n\n"
        printf "You must manually run:\n\n"
        echo "gcloud projects add-iam-policy-binding ${APIGEE_PROJECT} \
                 --condition=None \
                 --member=serviceAccount:${FULL_SA_EMAIL} \
                 --role=${ROLE}"
      fi
    done
  fi
}

# ====================================================================

check_shell_variables "${env_vars_to_check[@]}"

if [[ "${SERVICE_ACCOUNT}" == *"@"* ]]; then
  printf "The SERVICE_ACCOUNT variable should not contain an @ character.\n"
  exit 1
fi

FULL_SA_EMAIL="${SERVICE_ACCOUNT}@${CLOUDRUN_PROJECT}.iam.gserviceaccount.com"
check_and_maybe_create_sa
