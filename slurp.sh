#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-

rm -rf backend/src/main/resources/web/*
mkdir -p backend/src/main/resources/web/
cp -r frontend/dist/devportal-exp-frontend/browser/* backend/src/main/resources/web/
