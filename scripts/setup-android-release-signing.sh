#!/usr/bin/env bash
# Run on your own machine, NOT in GitHub Actions. Never commit the generated keystore.
set -euo pipefail
repo="jorgee16/investment_radar"
key_path="${1:-$HOME/.config/investment-radar/release-signing.jks}"
umask 077
for cmd in gh keytool base64 openssl; do
  command -v "$cmd" >/dev/null || { echo "Missing required command: $cmd" >&2; exit 1; }
done
gh auth status >/dev/null || { echo "First run: gh auth login" >&2; exit 1; }
mkdir -p "$(dirname "$key_path")"
if [[ -e "$key_path" ]]; then
  echo "Using existing keystore: $key_path (never overwrite this file)."
else
  echo "Creating a NEW permanent signing identity."
  echo "WARNING: it will NOT update installations signed by the old temporary key (including 0.6.3)."
fi
read -rsp "Keystore password (keep this in a password manager): " password; echo
if [[ ${#password} -lt 16 ]]; then echo "Use at least 16 characters" >&2; exit 1; fi
export IR_ANDROID_STORE_PASSWORD="$password"
if [[ ! -e "$key_path" ]]; then
  keytool -genkeypair -keystore "$key_path" -storepass:env IR_ANDROID_STORE_PASSWORD -keypass:env IR_ANDROID_STORE_PASSWORD \
    -alias investment-radar -keyalg RSA -keysize 3072 -validity 10000 \
    -dname "CN=Investment Radar Release,O=Investment Radar,C=PT" -noprompt
fi
keytool -list -v -keystore "$key_path" -storepass:env IR_ANDROID_STORE_PASSWORD -alias investment-radar |
  grep 'SHA256:' | head -1
echo "Uploading protected credentials to GitHub Actions secrets..."
base64 < "$key_path" | tr -d '\n' | gh secret set ANDROID_KEYSTORE_BASE64 --repo "$repo"
printf '%s' "$password" | gh secret set ANDROID_KEYSTORE_PASSWORD --repo "$repo"
unset password IR_ANDROID_STORE_PASSWORD
echo "Configured. KEEP a secure offline backup of $key_path and its password."
echo "Do not delete the old installed 0.6.3 until provider keys and portfolio are recorded."
