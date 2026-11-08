#!/bin/sh
# Baut Nivellement.jar (Java 17+)
set -e
rm -rf build && mkdir -p build dist
javac --release 17 -encoding UTF-8 -d build src/nivellement/*.java
mkdir -p build/nivellement/bilder build/nivellement/hilfe && cp src/nivellement/bilder/*.png build/nivellement/bilder/ && cp src/nivellement/hilfe/* build/nivellement/hilfe/
jar cfm dist/Nivellement.jar MANIFEST.MF -C build .
echo "Fertig: dist/Nivellement.jar"
