#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-

source ./shlib/utils.sh

check_shell_variables FB_APP_ID \
  FB_MSGING_SENDER_ID \
  FB_STORAGE_BUCKET \
  FB_PROJECT_ID \
  FB_AUTH_DOMAIN \
  FB_API_KEY

cd frontend
rm -fr dist
npm run build
