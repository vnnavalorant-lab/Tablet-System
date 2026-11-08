#!/bin/sh
# Compila e roda o teste headless do núcleo (sem Minecraft). Requer JDK 17+.
set -e
cd "$(dirname "$0")/.."
mkdir -p /tmp/mci-build
javac -d /tmp/mci-build $(find src/main/java/dev/mcinside/virtual -name '*.java') tools/CoreTest.java
java -Djava.awt.headless=true -cp /tmp/mci-build CoreTest
