#!/bin/bash

# Exit on any failure
set -e

flyctl deploy --local-only --config fly.toml


#!/usr/bin/env bash
sbt compile
sbt docker:publishLocal
docker-compose up