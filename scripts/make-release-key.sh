#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 The Nulis Launcher authors
#
# Makes the key Nulis release builds are signed with, on your own computer, once.
#
#   scripts/make-release-key.sh
#
# It writes two files into ~/.nulis-release/ and nowhere else:
#
#   release.jks            the key itself
#   keystore.properties    where it is and its passwords, which the Gradle build reads
#
# Neither is ever committed: they live outside the repository. With them in place,
# `./gradlew assemblePlayRelease bundlePlayRelease assembleGithubRelease` produces signed builds;
# without them the same commands still work and produce unsigned ones.
#
# For Google Play this is your UPLOAD key. Play App Signing keeps the key the store actually
# signs with; if this one is ever lost, Play can reset the upload key. For GitHub releases this
# is the key people's phones will expect every update to be signed with, so back it up: a lost
# key means everybody who installed from GitHub has to uninstall to update.
#
# Needs `keytool`, which comes with any JDK.

set -euo pipefail

dir="$HOME/.nulis-release"
jks="$dir/release.jks"
props="$dir/keystore.properties"
alias="nulis"

if ! command -v keytool >/dev/null 2>&1; then
  echo "keytool was not found. Install a JDK (17 or newer) and run this again." >&2
  exit 1
fi

if [ -e "$jks" ] || [ -e "$props" ]; then
  echo "A release key already exists in $dir." >&2
  echo "Refusing to overwrite it: a new key cannot update apps signed with the old one." >&2
  exit 1
fi

mkdir -p "$dir"
chmod 700 "$dir"

echo "Choose a password for the key (at least 6 characters). It is not echoed."
read -r -s -p "Password: " password
echo
read -r -s -p "Again: " again
echo
if [ "$password" != "$again" ]; then
  echo "The two passwords are different. Nothing was made." >&2
  exit 1
fi
if [ "${#password}" -lt 6 ]; then
  echo "keytool needs at least 6 characters. Nothing was made." >&2
  exit 1
fi

read -r -p "Your name or your project's, for the certificate [Nulis]: " name
name="${name:-Nulis}"

# 4096-bit RSA, valid for 30 years: Google Play asks for a key valid past 2033.
keytool -genkeypair \
  -keystore "$jks" \
  -storetype PKCS12 \
  -alias "$alias" \
  -keyalg RSA -keysize 4096 \
  -validity 10950 \
  -dname "CN=$name" \
  -storepass "$password" \
  -keypass "$password"

umask 077
cat > "$props" <<PROPS
storeFile=$jks
storePassword=$password
keyAlias=$alias
keyPassword=$password
PROPS
chmod 600 "$jks" "$props"

cat <<DONE

Done. Your release key is in $dir.

  1. Back up that whole folder somewhere safe and offline (a password manager, an encrypted
     USB stick). Do not put it in the repository, in a cloud folder you share, or in an email.
  2. Build:  ./gradlew bundlePlayRelease        (for Google Play: app-play-release.aab)
             ./gradlew assembleGithubRelease    (for GitHub releases: app-github-release.apk)
  3. Optional, for signed builds on GitHub Actions, add four repository secrets:
       NULIS_RELEASE_KEYSTORE_BASE64   base64 -w0 "$jks"
       NULIS_RELEASE_STORE_PASSWORD    the password you just chose
       NULIS_RELEASE_KEY_ALIAS         $alias
       NULIS_RELEASE_KEY_PASSWORD      the password you just chose
DONE
