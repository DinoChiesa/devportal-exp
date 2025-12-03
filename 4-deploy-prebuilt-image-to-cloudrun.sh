#!/bin/bash
# -*- mode: shell-script; sh-shell: bash; coding: utf-8 -*-

source ./shlib/utils.sh

check_shell_variables CLOUDRUN_PROJECT \
  CLOUDRUN_REGION \
  REPOSITORY_PROJECT \
  APIGEE_PROJECT \
  SERVICE_ACCOUNT

if [[ "${SERVICE_ACCOUNT}" == *"@"* ]]; then
  printf "The SERVICE_ACCOUNT variable should not contain an @ character.\n"
  exit 1
fi

FULL_SA_EMAIL="${SERVICE_ACCOUNT}@${CLOUDRUN_PROJECT}.iam.gserviceaccount.com"

gcloud run deploy devportal-exp \
  --image gcr.io/${REPOSITORY_PROJECT}/cloud-builds-submit/devportal-exp-backend-container:20250411 \
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
