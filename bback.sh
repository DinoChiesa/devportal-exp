#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-

cd backend
MAVEN_OPTS="--enable-native-access=ALL-UNNAMED" mvn clean package
