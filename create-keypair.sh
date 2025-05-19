#!/usr/bin/bash
# -*- mode: shell-script; sh-shell: bash; coding: utf-8 -*-

# Copyright 2025 Google LLC
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

if ! command -v "openssl" &>/dev/null; then
  printf "\nThe openssl command is missing; it must be available on the path.\n"
  exit 1
fi

cd backend/src/main/resources/keys

TIMESTAMP=$(date +%Y%m%d-%H%M)
openssl genpkey -algorithm rsa -pkeyopt rsa_keygen_bits:2048 -out "issuer-rsa-private-key-${TIMESTAMP}.pem"
openssl req -new -x509 -sha256 -days 3650 \
  -key "issuer-rsa-private-key-${TIMESTAMP}.pem" \
  -out "issuer-certificate-${TIMESTAMP}.pem" \
  -subj "/C=US/ST=Washington/L=Kirkland/O=Google LLC/OU=Apigee/CN=Apigee Demonstration Portal ${TIMESTAMP} Test Root CA" \
  -addext "basicConstraints=critical,CA:TRUE" \
  -addext "keyUsage=critical,keyCertSign,cRLSign" \
  -addext "extendedKeyUsage=serverAuth,clientAuth" \
  -addext "subjectKeyIdentifier=hash"

ls -lrt *.pem
