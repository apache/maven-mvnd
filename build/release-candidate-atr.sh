#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

#
# Prepare a release candidate and upload it to ATR (https://releases.apache.org/projects/maven-mvnd)
# instead of committing it to https://dist.apache.org/repos/dist/dev/maven/mvnd (see release-candidate.sh).
#
# Usage: build/release-candidate-atr.sh <version> [additional maven options, e.g. -Datr.dryRun=true]
#
# Must be run from the root of the project.
# ATR credentials are read from the "apache.atr" server in settings.xml:
# username is the ASF user ID, password is the ATR Personal Access Token.
#

# Enable for debug execution
# set -x

set -e
export VERSION=$1

if [ "${VERSION}x" = "x" ]
then
  echo "Specify the version: $0 <version> [maven options]"
  exit 1
fi
shift

if [ ! -f pom.xml ] || [ ! -d build ]
then
  echo "Run this script from the root of the project"
  exit 1
fi

RELEASE_DIR="$(pwd)/target/releases/mvnd/${VERSION}"

# version in ATR, for tests use a version with a suffix, e.g.:
# ATR_VERSION="${VERSION}-test"
ATR_VERSION="${VERSION}"

# fail early if the version does not exist in ATR or is not in the composing phase
./mvnw --non-recursive atr:check-composing \
  -Datr.version="${ATR_VERSION}" \
  "$@"

rm -Rf "${RELEASE_DIR}"
mkdir -p "${RELEASE_DIR}"
pushd "${RELEASE_DIR}"

gh release download "${VERSION}"

for dist in maven-mvnd*.*
do
  echo "Processing ${dist}"
  # sha256 are used by homebrew which does not support sha512 atm
  shasum -a 256 -b "${dist}" | cut -d ' ' -f 1 > "${dist}.sha256"
  shasum -a 512 -b "${dist}" | cut -d ' ' -f 1 > "${dist}.sha512"
  gpg --detach-sign --armor "${dist}"
done

# comma separated list of absolute paths of all files to upload
FILES=$(find "${RELEASE_DIR}" -maxdepth 1 -type f -name 'maven-mvnd*' | sort | paste -sd ',' -)

popd

./mvnw --non-recursive atr:upload \
  -Datr.version="${ATR_VERSION}" \
  -Datr.files="${FILES}" \
  "$@"

echo
echo "Release candidate ${ATR_VERSION} uploaded to ATR."
echo "Start the vote in ATR using 'Start voting' on https://releases.apache.org/compose/maven-mvnd/${ATR_VERSION}"
