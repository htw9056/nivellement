#!/bin/sh
# Baut das Windows-Paket (ohne installiertes Java lauffähig) unter Linux.
#
# Voraussetzungen: Go, Python 3, ein Linux-JDK und ein Windows-JDK gleicher Version
# (z. B. Eclipse Temurin 21), entpackt.
#
# Aufruf: windows/paket_bauen.sh <Linux-JDK-Ordner> <Windows-JDK-Ordner>
set -e
LINUX_JDK="$1"
WIN_JDK="$2"
if [ -z "$LINUX_JDK" ] || [ -z "$WIN_JDK" ]; then
    echo "Aufruf: $0 <Linux-JDK-Ordner> <Windows-JDK-Ordner>"
    exit 1
fi
cd "$(dirname "$0")/.."
VERSION=$(sed -n 's/.*VERSION = "V\([0-9.]*\)".*/\1/p' src/nivellement/Version.java)
DATEIVERSION="$(echo "$VERSION" | cut -d. -f1).$(echo "$VERSION" | cut -d. -f2 | sed "s/^0*//; s/^$/0/").0.0"
ZIEL="dist/Nivellement_V${VERSION}_Windows"

./bauen.sh
rm -rf "$ZIEL" && mkdir -p "$ZIEL/app"
cp dist/Nivellement.jar "$ZIEL/app/"

echo "Java-Laufzeit erzeugen ..."
"$LINUX_JDK/bin/jlink" --module-path "$WIN_JDK/jmods" \
    --add-modules java.base,java.desktop,java.prefs,jdk.charsets,jdk.localedata --include-locales=de,en \
    --strip-debug --no-header-files --no-man-pages --compress=zip-9 --output "$ZIEL/runtime"

echo "Starter bauen ..."
cd windows/starter
python3 ../ressourcen.py ../Nivellement.ico rsrc_windows_amd64.syso "$DATEIVERSION" \
    "Nivellement - Nivellementauswertung" "Nivellement.exe"
GOOS=windows GOARCH=amd64 go build -trimpath -ldflags "-s -w -H windowsgui" -o "../../$ZIEL/Nivellement.exe" .
python3 ../ressourcen.py ../Nivellement.ico rsrc_windows_amd64.syso "$DATEIVERSION" \
    "Nivellement - Konsole" "NivellementKonsole.exe"
GOOS=windows GOARCH=amd64 go build -tags konsole -trimpath -ldflags "-s -w" -o "../../$ZIEL/NivellementKonsole.exe" .
rm -f rsrc_windows_amd64.syso
cd ../..

cp -r beispiele "$ZIEL/"
cp LICENSE "$ZIEL/LIZENZ.txt"
cp windows/LIESMICH.txt "$ZIEL/"
cp windows/DRITTANBIETER.txt "$ZIEL/"
sed "s/@VERSION@/Nivellement V$VERSION/" src/nivellement/hilfe/hilfe.html > "$ZIEL/Handbuch.html"
sed "s/@VERSION@/Nivellement V$VERSION/" src/nivellement/hilfe/hilfe.html > docs/Handbuch.html
sed -i "s/@VERSION@/V$VERSION/" "$ZIEL/LIESMICH.txt"
(cd dist && rm -f "Nivellement_V${VERSION}_Windows.zip" && zip -qr "Nivellement_V${VERSION}_Windows.zip" "Nivellement_V${VERSION}_Windows")
echo "Fertig: dist/Nivellement_V${VERSION}_Windows.zip"
