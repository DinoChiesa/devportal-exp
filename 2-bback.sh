#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-

#source ./shlib/utils.sh

#check_shell_variables CLOUDRUN_PROJECT 

cd backend
MAVEN_OPTS="--enable-native-access=ALL-UNNAMED" mvn clean package
