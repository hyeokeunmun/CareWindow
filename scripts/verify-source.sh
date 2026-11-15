#!/usr/bin/env bash
# Source/API verification only. Does NOT produce or execute an Android APK.
set -euo pipefail
cd "$(dirname "$0")/.."
CACHE="${CAREWINDOW_VERIFY_CACHE:-$PWD/verification-cache}"
OUT="$PWD/build/source-verification"
mkdir -p "$CACHE" "$OUT"
while IFS=$'\t' read -r name checksum url encoding; do
    [[ -z "$name" || "$name" == \#* ]] && continue
    target="$CACHE/$name"
    if [[ ! -f "$target" ]]; then
        echo "Downloading $name from official source or Maven Central"
        curl --fail --location --silent --show-error --max-time 180 "$url" -o "$target.transfer"
        if [[ "$encoding" == "base64" ]]; then
            python3 -c 'import base64,sys; open(sys.argv[2],"wb").write(base64.b64decode(open(sys.argv[1],"rb").read()))' "$target.transfer" "$target"
            rm "$target.transfer"
        else
            mv "$target.transfer" "$target"
        fi
    fi
    python3 - "$target" "$checksum" <<'PY'
import hashlib, pathlib, sys
p = pathlib.Path(sys.argv[1])
actual = hashlib.sha256(p.read_bytes()).hexdigest()
if actual != sys.argv[2]:
    raise SystemExit(f"Checksum mismatch: {p.name}. Stop and review upstream; do not silently replace the pin.")
PY
done < scripts/verification-dependencies.tsv
unzip -p "$CACHE/Vehicle-2.3.2.aar" classes.jar > "$CACHE/vehicle-api.jar"
unzip -p "$CACHE/NaviHelper-3.0.5.aar" classes.jar > "$CACHE/navi-helper-api.jar"
unzip -p "$CACHE/navi-data-3.0.4.aar" classes.jar > "$CACHE/navi-data-api.jar"
mapfile -d '' core_sources < <(find core/src -name '*.kt' -print0)
mapfile -d '' app_sources < <(find core/src/main app/src/main -name '*.kt' -print0)
compiler=(java -cp "$CACHE/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17)
test_cp="$CACHE/kotlin-stdlib.jar:$CACHE/junit.jar:$CACHE/hamcrest.jar"
"${compiler[@]}" -classpath "$test_cp" -d "$OUT/core-tests.jar" "${core_sources[@]}"
java -cp "$OUT/core-tests.jar:$test_cp" org.junit.runner.JUnitCore \
    ai.carewindow.core.CarePlannerTest ai.carewindow.core.HandoffSessionTest ai.carewindow.core.WaypointReadTrackerTest | tee "$OUT/unit-tests.txt"
app_cp="$CACHE/kotlin-stdlib.jar:$CACHE/android.jar:$CACHE/android.car.jar:$CACHE/vehicle-api.jar:$CACHE/navi-helper-api.jar:$CACHE/navi-data-api.jar"
"${compiler[@]}" -classpath "$app_cp" -d "$OUT/app-typecheck.jar" "${app_sources[@]}" 2>&1 | tee "$OUT/typecheck.txt"
python3 scripts/check-contracts.py | tee "$OUT/contracts.txt"
echo "PASS: JVM tests, Kotlin/SDK source type-check and static manifest contracts. This command does not verify APK/lint/UI/runtime; see docs/VERIFICATION.md for separately executed checks."
