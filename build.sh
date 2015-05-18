#!/bin/sh

# Parallel executions of maven modules and tests.
# Half of CPU core are used in to keep other half for OS and other programs.
cd server/sonar-web && npm install && npm run test && npm run build && cd ../..
mvn clean install -e -B -T0.5C -DforkCount=0.5C $*
