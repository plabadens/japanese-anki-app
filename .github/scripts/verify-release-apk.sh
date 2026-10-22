#!/usr/bin/env bash
# Assert that a release APK is genuinely release-signed, arm64-only, and carries the version
# the release notes claim. Called from .github/workflows/release-apk.yml inside the flake dev
# shell, after assembleRelease.
#
# Usage: verify-release-apk.sh <versionName> <versionCode> [distDir]
set -euo pipefail

version_name="${1:?versionName is required}"
version_code="${2:?versionCode is required}"
dist="${3:-dist}"

: "${ANDROID_HOME:?ANDROID_HOME must point at the flake-pinned SDK}"
build_tools="$ANDROID_HOME/build-tools/36.0.0"
apksigner="$build_tools/apksigner"
aapt2="$build_tools/aapt2"

for tool in "$apksigner" "$aapt2"; do
  if [[ ! -x "$tool" ]]; then
    printf 'missing build-tools binary: %s\n' "$tool" >&2
    exit 1
  fi
done

release_dir="app/build/outputs/apk/release"
signed_apk="$release_dir/app-arm64-v8a-release.apk"
unsigned_apk="$release_dir/app-arm64-v8a-release-unsigned.apk"

if [[ -f "$unsigned_apk" && ! -f "$signed_apk" ]]; then
  printf '%s\n' \
    'assembleRelease produced an UNSIGNED APK.' \
    'The signing environment (JAPANESE_ANKI_RELEASE_KEYSTORE and friends) did not reach Gradle,' \
    'so app/build.gradle.kts fell back to the unsigned release variant. Refusing to publish.' >&2
  exit 1
fi

if [[ ! -f "$signed_apk" ]]; then
  printf 'expected %s to exist\n' "$signed_apk" >&2
  exit 1
fi

install -d -m 0755 "$dist"
apk_name="japanese-anki-${version_name}-arm64-v8a-release.apk"
cp "$signed_apk" "$dist/$apk_name"

python3 scripts/verify-apk-abi.py "$dist/$apk_name" arm64-v8a

"$apksigner" verify --print-certs --verbose "$dist/$apk_name" > "$dist/apksigner-verify.txt"
if ! grep -qE '^Verified using v[23] scheme \(APK Signature Scheme v[23]\): true' "$dist/apksigner-verify.txt"; then
  printf '%s\n' 'APK carries no verified v2/v3 signature; refusing to publish' >&2
  exit 1
fi

digest="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$dist/apksigner-verify.txt" | head -n 1)"
if [[ -z "$digest" ]]; then
  printf '%s\n' 'could not read the signer certificate digest' >&2
  exit 1
fi

# Optional pin: set the repository variable ANDROID_RELEASE_CERT_SHA256 to the lowercase hex
# digest of the release certificate. This is public information, not a secret.
if [[ -n "${ANDROID_RELEASE_CERT_SHA256:-}" ]]; then
  expected="$(printf '%s' "$ANDROID_RELEASE_CERT_SHA256" | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
  actual="$(printf '%s' "$digest" | tr '[:upper:]' '[:lower:]')"
  if [[ "$actual" != "$expected" ]]; then
    printf 'signer digest %s does not match ANDROID_RELEASE_CERT_SHA256 %s\n' "$actual" "$expected" >&2
    exit 1
  fi
fi

"$aapt2" dump badging "$dist/$apk_name" > "$dist/badging.txt"
grep -qF "name='dev.pierre.japaneseanki'" "$dist/badging.txt" \
  || { printf '%s\n' 'unexpected applicationId in APK' >&2; exit 1; }
grep -qF "versionCode='$version_code'" "$dist/badging.txt" \
  || { printf 'APK versionCode does not match app/build.gradle.kts (%s)\n' "$version_code" >&2; exit 1; }
grep -qF "versionName='$version_name'" "$dist/badging.txt" \
  || { printf 'APK versionName does not match app/build.gradle.kts (%s)\n' "$version_name" >&2; exit 1; }
if grep -qF 'application-debuggable' "$dist/badging.txt"; then
  printf '%s\n' 'release APK is debuggable; refusing to publish' >&2
  exit 1
fi

(
  cd "$dist"
  sha256sum "$apk_name" > SHA256SUMS.txt
  stat --printf='%n %s bytes\n' "$apk_name" SHA256SUMS.txt
)

printf 'signer sha256: %s\n' "$digest"
