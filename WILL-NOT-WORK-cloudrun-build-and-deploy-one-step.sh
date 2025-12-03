#!/bin/bash
# -*- mode: shell-script; sh-shell: bash; coding: utf-8 -*-

env_vars_to_check=(
  "CLOUDRUN_PROJECT"
  "CLOUDRUN_REGION"
  "APIGEE_PROJECT"
  "SERVICE_ACCOUNT"
)

source ./shlib/utils.sh

check_shell_variables "${env_vars_to_check[@]}"

if [[ "${SERVICE_ACCOUNT}" == *"@"* ]]; then
  printf "The SERVICE_ACCOUNT variable should not contain an @ character.\n"
  exit 1
fi

printf "This script will not work, as is. The build is a 2-step build, and I'd need to\n"
printf "provide a cloudbuild.yaml file for this. And I haven't done so yet.\n\n"
printf "To build and depoy on cloud run, ... \n"
printf "   ./bimage.sh\n"
printf "   ./cloudrun-deploy-prebuilt-image.sh\n\n"

exit 1

FULL_SA_EMAIL="${SERVICE_ACCOUNT}@${CLOUDRUN_PROJECT}.iam.gserviceaccount.com"

gcloud run deploy devportal-exp \
  --source . \
  --cpu 1 \
  --set-env-vars "APIGEE_PROJECT=${APIGEE_PROJECT}" \
  --memory '512Mi' \
  --min-instances 0 \
  --max-instances 1 \
  --allow-unauthenticated \
  --service-account ${FULL_SA_EMAIL} \
  --project ${CLOUDRUN_PROJECT} \
  --region ${CLOUDRUN_REGION} \
  --timeout 300
