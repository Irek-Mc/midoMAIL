#!/bin/bash
set -euo pipefail

# =============================================================================
# midoMAIL Release Pipeline
# Dokumentacja: KEYSTORE.md (podpisywanie Android),
#               Documentation/40-Platforms/41-JVM.md, 40-Android.md,
#               Documentation/50-Quality/52-Deployment.md
#
# Cel: ktoś klonuje repozytorium i uruchamia `./release.sh` bez zastanawiania
# się, co musi mieć zainstalowane - skrypt sam sprawdza wymagania, sam
# generuje klucz podpisywania Android jeśli trzeba (KEYSTORE.md), i produkuje
# gotową paczkę do pobrania.
#
# Artefakty:
#   :platform-jvm  - Communication Gateway jako przenośna dystrybucja JVM
#                     (Email+WebSocket+REST+CLI+UI w jednym procesie,
#                     ADR-0036) - działa na Linux/macOS/Windows, wymaga
#                     wyłącznie JDK. Budowany ZAWSZE.
#   :platform-android - APK klienta Android (SMS/MMS <-> Gateway). Budowany
#                     TYLKO jeśli wykryty jest Android SDK - w innym wypadku
#                     pomijany bez przerywania buildu JVM (Android to jedna
#                     z kilku platform, nie wymóg - 43-Przenosnosc.md).
#
# Wynik: build/output/midoMAIL-vX.Y-buildNNNNN.zip
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

COUNTER_FILE="build/build.counter"
VERSION_FILE="version.txt"
OUTPUT_DIR="build/output"

KEYSTORE_FILE="${MIDOMAIL_KEYSTORE_FILE:-keys/midomail-release.jks}"
KEYSTORE_PASSWORD_FILE="keys/midomail-release.password"
KEY_ALIAS="${MIDOMAIL_KEY_ALIAS:-midomail}"

MIN_JAVA_VERSION=17
PRODUCT_NAME="midoMAIL"

ANDROID_AVAILABLE=0
ANDROID_APK_BUILT=0
_STATUS_JVM="?"
_STATUS_ANDROID="POMINIĘTY"
_STATUS_SHA256="?"

log()  { echo "[BUILD] $*" >&2; }
warn() { echo "[WARN]  $*" >&2; }
err()  { echo "[ERROR] $*" >&2; exit 1; }

# =============================================================================
# ETAP 1 — Walidacja środowiska
# =============================================================================
validate_environment() {
    log "ETAP 1 — Walidacja środowiska"
    command -v java      >/dev/null 2>&1 || err "java nie znaleziona - wymagany JDK ${MIN_JAVA_VERSION}+"
    command -v sha256sum >/dev/null 2>&1 || err "sha256sum nie znaleziony"
    command -v zip       >/dev/null 2>&1 || err "zip nie znaleziony"
    command -v unzip     >/dev/null 2>&1 || err "unzip nie znaleziony"
    [ -x "gradlew" ]                     || err "gradlew nie znaleziony lub niewykonywalny"

    local java_version
    java_version=$(java -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"')
    [ -n "$java_version" ] || err "Nie udało się odczytać wersji Javy"
    [ "$java_version" -ge "$MIN_JAVA_VERSION" ] || \
        err "Wykryto JDK ${java_version}, wymagany JDK ${MIN_JAVA_VERSION}+"

    log "Środowisko OK (JDK ${java_version})"
}

# =============================================================================
# ETAP 2 — Walidacja projektu
# =============================================================================
validate_project() {
    log "ETAP 2 — Walidacja projektu"
    [ -f "settings.gradle.kts" ] || err "settings.gradle.kts nie znaleziony"
    grep -q '":platform-jvm"' settings.gradle.kts || err "Moduł :platform-jvm nie jest zarejestrowany w settings.gradle.kts"
    [ -f "platform-jvm/build.gradle.kts" ] || err "platform-jvm/build.gradle.kts nie znaleziony"
    log "Projekt OK"
}

# =============================================================================
# ETAP 3 — Aktualizacja numeru Build
# =============================================================================
bump_build() {
    log "ETAP 3 — Aktualizacja numeru Build"
    mkdir -p "$(dirname "$COUNTER_FILE")"
    [ -f "$COUNTER_FILE" ] || echo 0 > "$COUNTER_FILE"
    local build_num
    build_num=$(cat "$COUNTER_FILE")
    build_num=$((build_num + 1))
    echo "$build_num" > "$COUNTER_FILE"
    log "Build: $build_num"
    echo "$build_num"
}

# =============================================================================
# ETAP 4 — Aktualizacja wersji
# =============================================================================
update_version() {
    local build_num="$1"
    log "ETAP 4 — Aktualizacja wersji"
    local minor
    minor=$(printf "%02d" "$build_num")
    local version="v0.${minor}"
    local build_date
    build_date=$(date +%Y-%m-%d)

    cat > "$VERSION_FILE" <<EOF
VERSION=${version}
BUILD=${build_num}
DATE=${build_date}
EOF

    log "Wersja: ${version}"
    echo "${version}"
}

# =============================================================================
# ETAP 5 — Wykrycie Android SDK
# =============================================================================
detect_android_sdk() {
    log "ETAP 5 — Wykrycie Android SDK"
    if [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "${ANDROID_SDK_ROOT}" ]; then
        log "Android SDK wykryty (ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT})"
        ANDROID_AVAILABLE=1
    elif [ -n "${ANDROID_HOME:-}" ] && [ -d "${ANDROID_HOME}" ]; then
        log "Android SDK wykryty (ANDROID_HOME=${ANDROID_HOME})"
        ANDROID_AVAILABLE=1
    elif [ -f "local.properties" ] && grep -q '^sdk.dir=' local.properties; then
        log "Android SDK wykryty (local.properties: sdk.dir)"
        ANDROID_AVAILABLE=1
    else
        warn "Android SDK nie znaleziony - pomijam build :platform-android (patrz Documentation/40-Platforms/40-Android.md)"
        ANDROID_AVAILABLE=0
    fi
}

# =============================================================================
# ETAP 6 — Klucz podpisywania Android (patrz KEYSTORE.md)
# =============================================================================
generate_password() {
    # `head -c` zamyka strumień wcześniej niż `tr` skończy czytać z /dev/urandom - `tr` dostaje
    # SIGPIPE (141), co pod `set -o pipefail` ubija cały skrypt przy `var=$(generate_password)`.
    # `|| true` gwarantuje, że status pipeline'a jest 0 mimo SIGPIPE w `tr`.
    LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom 2>/dev/null | head -c 32 || true
}

ensure_release_keystore() {
    log "ETAP 6 — Klucz podpisywania Android"
    mkdir -p "$(dirname "$KEYSTORE_FILE")"

    if [ -f "$KEYSTORE_FILE" ]; then
        log "Użycie istniejącego klucza: $KEYSTORE_FILE"
        if [ -n "${MIDOMAIL_KEYSTORE_PASSWORD:-}" ]; then
            : # hasło podane jawnie przez wywołującego (własny klucz, KEYSTORE.md §Nadpisanie)
        elif [ -f "$KEYSTORE_PASSWORD_FILE" ]; then
            MIDOMAIL_KEYSTORE_PASSWORD=$(cat "$KEYSTORE_PASSWORD_FILE")
        else
            err "Keystore $KEYSTORE_FILE istnieje, ale brak hasła - ustaw zmienną MIDOMAIL_KEYSTORE_PASSWORD (patrz KEYSTORE.md)"
        fi
    else
        command -v keytool >/dev/null 2>&1 || err "keytool nie znaleziony (część JDK) - wymagany do wygenerowania klucza"
        log "Brak klucza - generuję nowy: $KEYSTORE_FILE (patrz KEYSTORE.md)"
        MIDOMAIL_KEYSTORE_PASSWORD=$(generate_password)
        keytool -genkeypair -v \
            -keystore "$KEYSTORE_FILE" \
            -alias "$KEY_ALIAS" \
            -keyalg RSA -keysize 2048 -validity 10000 \
            -storetype PKCS12 \
            -storepass "$MIDOMAIL_KEYSTORE_PASSWORD" \
            -keypass "$MIDOMAIL_KEYSTORE_PASSWORD" \
            -dname "CN=midoMAIL, OU=midoMAIL, O=midoMAIL, C=PL" \
            >/dev/null 2>&1
        echo -n "$MIDOMAIL_KEYSTORE_PASSWORD" > "$KEYSTORE_PASSWORD_FILE"
        chmod 600 "$KEYSTORE_FILE" "$KEYSTORE_PASSWORD_FILE"
        log "Nowy klucz wygenerowany (lokalnie, poza gitem - .gitignore: keys/)"
    fi

    export MIDOMAIL_KEYSTORE_PASSWORD
    export MIDOMAIL_KEYSTORE_FILE="$KEYSTORE_FILE"
    export MIDOMAIL_KEY_ALIAS="$KEY_ALIAS"
}

# =============================================================================
# ETAP 7 — Kompilacja dystrybucji :platform-jvm
# =============================================================================
compile_jvm_distribution() {
    log "ETAP 7 — Kompilacja dystrybucji :platform-jvm"
    ./gradlew :platform-jvm:distZip --no-daemon --quiet
    local zip_path
    zip_path=$(find platform-jvm/build/distributions -maxdepth 1 -name "*.zip" | head -1)
    [ -n "$zip_path" ] || err "Dystrybucja :platform-jvm nie została wygenerowana"
    log "Dystrybucja JVM — OK ($(du -sh "$zip_path" | cut -f1))"
    echo "$zip_path"
}

# =============================================================================
# ETAP 8 — Weryfikacja dystrybucji :platform-jvm
# =============================================================================
verify_jvm_distribution() {
    local zip_path="$1"
    log "ETAP 8 — Weryfikacja dystrybucji :platform-jvm"
    local contents
    contents=$(unzip -l "$zip_path" 2>/dev/null | awk 'NR>3{print $4}')

    local failed=0
    echo "$contents" | grep -qE '/bin/platform-jvm$'     && log "  [OK] bin/platform-jvm"     || { warn "  [BRAK] bin/platform-jvm"; failed=$((failed+1)); }
    echo "$contents" | grep -qE '/bin/platform-jvm\.bat$' && log "  [OK] bin/platform-jvm.bat" || { warn "  [BRAK] bin/platform-jvm.bat"; failed=$((failed+1)); }
    local jar_count
    jar_count=$(echo "$contents" | grep -cE '/lib/.*\.jar$' || true)
    if [ "$jar_count" -gt 0 ]; then
        log "  [OK] lib/*.jar ($jar_count plików)"
    else
        warn "  [BRAK] lib/*.jar"
        failed=$((failed+1))
    fi

    [ "$failed" -eq 0 ] || err "Walidacja dystrybucji JVM FAILED — $failed błąd(ów)"
    log "Weryfikacja dystrybucji JVM — OK"
    _STATUS_JVM="OK"
}

# =============================================================================
# ETAP 9 — Kompilacja i weryfikacja APK :platform-android (opcjonalna)
# =============================================================================
compile_android_apk() {
    log "ETAP 9 — Kompilacja APK :platform-android"
    ./gradlew :platform-android:assembleRelease --no-daemon --quiet
    local apk_path
    apk_path=$(find platform-android/build/outputs/apk/release -maxdepth 1 -name "*.apk" | head -1)
    [ -n "$apk_path" ] || err "APK Release :platform-android nie został wygenerowany"

    local apksigner
    apksigner=$(find "${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}/build-tools" \
        -name "apksigner" -type f 2>/dev/null | sort -V | tail -1 || true)
    if [ -n "$apksigner" ]; then
        if "$apksigner" verify "$apk_path" >/dev/null 2>&1; then
            log "APK Release — podpis zweryfikowany"
        else
            err "APK Release — podpis niepoprawny lub brakujący"
        fi
    else
        warn "apksigner nie znaleziony — pomijam weryfikację podpisu"
    fi

    log "APK Release — OK ($(du -sh "$apk_path" | cut -f1))"
    echo "$apk_path"
}

# =============================================================================
# ETAP 10 — Budowa finalnej paczki
# =============================================================================
package_release() {
    local jvm_zip="$1"
    local android_apk="$2"
    local version="$3"
    local build_num="$4"

    log "ETAP 10 — Budowa finalnej paczki"

    local package_name="${PRODUCT_NAME}-${version}-build$(printf "%05d" "$build_num").zip"
    mkdir -p "$OUTPUT_DIR"
    local abs_out_dir
    abs_out_dir="$(cd "$SCRIPT_DIR" && realpath "$OUTPUT_DIR")"
    local staging="${abs_out_dir}/package-staging"
    local package_zip="${abs_out_dir}/${package_name}"
    local build_date
    build_date=$(date +%Y-%m-%d)
    local git_commit
    git_commit=$(git -C "$SCRIPT_DIR" rev-parse --short HEAD 2>/dev/null || echo "n/a")

    rm -rf "$staging"
    mkdir -p "$staging/gateway-jvm"

    # --- Rozpakuj dystrybucję JVM do stabilnej nazwy katalogu (niezależnej od Gradle) ---
    unzip -qq "$jvm_zip" -d "${staging}/_jvm-raw"
    local inner_dir
    inner_dir=$(find "${staging}/_jvm-raw" -mindepth 1 -maxdepth 1 -type d | head -1)
    cp -r "${inner_dir}/." "${staging}/gateway-jvm/"
    rm -rf "${staging}/_jvm-raw"
    chmod 755 "${staging}/gateway-jvm/bin/platform-jvm"

    # --- Dołącz APK Android, jeśli zbudowany ---
    if [ -n "$android_apk" ]; then
        mkdir -p "${staging}/android"
        cp "$android_apk" "${staging}/android/midoMAIL-release.apk"
    fi

    # --- Metadane wersji ---
    cat > "${staging}/VERSION.txt" <<EOF
product=${PRODUCT_NAME}
version=${version}
build=${build_num}
date=${build_date}
gitCommit=${git_commit}
androidIncluded=$([ -n "$android_apk" ] && echo "true" || echo "false")
EOF

    # --- Instrukcja uruchomienia ---
    cat > "${staging}/URUCHOMIENIE.md" <<'EOF'
# midoMAIL — Uruchomienie

## Gateway (JVM) — gateway-jvm/

Wymaga wyłącznie JDK 17+ zainstalowanego na maszynie (Linux/macOS/Windows).

Linux/macOS:
    ./gateway-jvm/bin/platform-jvm

Windows:
    gateway-jvm\bin\platform-jvm.bat

Przy pierwszym uruchomieniu proces tworzy w katalogu roboczym pliki
`config.properties` i `secrets.properties` oraz losowy klucz Admin API,
wypisywany w konsoli (X-API-Key). Domyślne porty: 8080 (Admin REST API),
8081 (UI). Adaptery (Email, WebSocket) aktywują się dopiero po skonfigurowaniu
poświadczeń przez Admin API/CLI/UI — bez konfiguracji proces startuje z samym
szkieletem administracyjnym.

## Aplikacja Android — android/midoMAIL-release.apk (jeśli obecna)

Zainstaluj APK na urządzeniu z Androidem 9+ (minSdk 28). midoMAIL przy
pierwszym uruchomieniu poprosi o rolę domyślnej aplikacji SMS/MMS — jest to
wymagane, nie opcjonalne (patrz Documentation/40-Platforms/40-Android.md).

Klucz podpisywania tego APK został wygenerowany lokalnie na maszynie, która
zbudowała tę paczkę (patrz KEYSTORE.md w repozytorium źródłowym) — aktualizacje
tej instalacji wymagają paczek zbudowanych tym samym kluczem.
EOF

    # --- Checksums ---
    (cd "$staging" && \
        find . -type f ! -name "checksums.sha256" | sort | while read -r f; do
            sha256sum "$f" | awk -v r="${f#./}" '{print $1 "  " r}'
        done > checksums.sha256)
    log "  checksums.sha256: $(wc -l < "$staging/checksums.sha256") wpisów"

    # --- Pakuj ZIP ---
    (cd "$staging" && zip -r -q "$package_zip" .)

    rm -rf "$staging"
    log "  Paczka: $package_name ($(du -sh "$package_zip" | cut -f1))"
    echo "$package_name"
}

# =============================================================================
# ETAP 11 — Walidacja finalnej paczki
# =============================================================================
validate_package() {
    local zip_path="$1"
    local android_expected="$2"
    log "ETAP 11 — Walidacja finalnej paczki"

    [ -f "$zip_path" ] || err "Paczka nie znaleziona: $zip_path"
    local contents
    contents=$(unzip -l "$zip_path" 2>/dev/null | awk 'NR>3{print $4}')
    local failed=0

    for f in "gateway-jvm/bin/platform-jvm" "gateway-jvm/bin/platform-jvm.bat" "VERSION.txt" "URUCHOMIENIE.md" "checksums.sha256"; do
        if echo "$contents" | grep -qF "$f"; then
            log "  [OK] $f"
        else
            warn "  [BRAK] $f"
            failed=$((failed+1))
        fi
    done

    if [ "$android_expected" = "1" ]; then
        if echo "$contents" | grep -qF "android/midoMAIL-release.apk"; then
            log "  [OK] android/midoMAIL-release.apk"
        else
            warn "  [BRAK] android/midoMAIL-release.apk"
            failed=$((failed+1))
        fi
    fi

    [ "$failed" -eq 0 ] || err "Walidacja paczki FAILED — $failed błąd(ów)"
    log "Walidacja paczki — OK"
}

# =============================================================================
# ETAP 12 — SHA-256 artefaktu końcowego
# =============================================================================
compute_package_sha256() {
    local zip_path="$1"
    log "ETAP 12 — SHA-256"
    local sha
    sha=$(sha256sum "$zip_path" | awk '{print $1}')
    log "SHA-256: $sha"
    _STATUS_SHA256="OK"
    echo "$sha"
}

# =============================================================================
# Release Summary
# =============================================================================
release_summary() {
    local package_name="$1"
    local package_zip="$2"

    _sym() {
        case "$1" in
            OK) echo "✓ OK" ;;
            POMINIĘTY) echo "— POMINIĘTY" ;;
            *) echo "✗ FAIL" ;;
        esac
    }

    echo ""
    echo "========================================"
    echo " Release Summary"
    echo "========================================"
    echo ""
    echo " Gateway JVM (:platform-jvm)"
    echo "   $(_sym "$_STATUS_JVM")"
    echo ""
    echo " APK Android (:platform-android)"
    echo "   $(_sym "$_STATUS_ANDROID")"
    echo ""
    echo " SHA-256"
    echo "   $(_sym "$_STATUS_SHA256")"
    echo ""
    echo "========================================"
    echo ""
    echo " Output:"
    echo " $package_name"
    echo " $(du -sh "$package_zip" | cut -f1)"
    echo ""
    echo " Uruchomienie: patrz URUCHOMIENIE.md w paczce."
    echo ""
    echo "========================================"
    echo ""

    [ "$_STATUS_JVM" = "OK" ] || err "Release Summary: Gateway JVM FAILED"
}

# =============================================================================
# Główny pipeline
# =============================================================================
main() {
    echo ""
    echo "============================================================"
    echo " midoMAIL Release Pipeline"
    echo "============================================================"
    echo ""

    validate_environment
    validate_project

    local build_num version
    build_num=$(bump_build)
    version=$(update_version "$build_num")

    detect_android_sdk

    local android_apk=""
    if [ "$ANDROID_AVAILABLE" -eq 1 ]; then
        ensure_release_keystore
    fi

    local jvm_zip
    jvm_zip=$(compile_jvm_distribution)
    verify_jvm_distribution "$jvm_zip"

    if [ "$ANDROID_AVAILABLE" -eq 1 ]; then
        android_apk=$(compile_android_apk)
        if [ -n "$android_apk" ]; then
            _STATUS_ANDROID="OK"
            ANDROID_APK_BUILT=1
        fi
    fi

    local package_name package_zip
    package_name=$(package_release "$jvm_zip" "$android_apk" "$version" "$build_num")
    package_zip="$(cd "$SCRIPT_DIR" && realpath "$OUTPUT_DIR")/${package_name}"

    validate_package "$package_zip" "$ANDROID_APK_BUILT"
    compute_package_sha256 "$package_zip" > /dev/null

    release_summary "$package_name" "$package_zip"
}

main "$@"
