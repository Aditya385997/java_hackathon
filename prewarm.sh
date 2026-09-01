#!/usr/bin/env bash
# Run this TONIGHT on good wifi. Pulls every dependency into ~/.m2 so the venue
# network cannot stall you mid-hackathon.
set -e
./mvnw -B dependency:go-offline
./mvnw -B clean verify
echo
echo "Dependencies cached and build is green. You are ready."
