#!/usr/bin/env sh
# Compiles the project and creates dist/bank-robbery.jar (requires JDK 11 or later).
set -e
cd "$(dirname "$0")"
rm -rf build dist
mkdir -p build/classes dist
javac --release 11 -encoding UTF-8 -nowarn -d build/classes $(find src -name "*.java")
cp src/simulation.properties build/classes/
jar --create --file dist/bank-robbery.jar --main-class alloverdev.Main -C build/classes .
echo "Created dist/bank-robbery.jar - run it with: java -jar dist/bank-robbery.jar"
