#!/usr/bin/env bash
#
# Copyright 2019 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#


set -e
#set -x

VERSION=$1

if [ "${VERSION}x" = "x" ]
then
  echo "Specify the version: $0 [version]"
  exit 1
fi

echo "SDKMAN_CONSUMER_KEY: $(echo ${SDKMAN_CONSUMER_KEY} | cut -c-3)..."
echo "SDKMAN_CONSUMER_TOKEN: $(echo ${SDKMAN_CONSUMER_TOKEN} | cut -c-3)..."

echo "Publishing version ${VERSION} on sdkman.io"

function publishRelease() {
    VERSION=$1
    SDKMAN_PLATFORM=$2
    MVND_PLATFORM=$3
    QUALIFIER=$4

    FILE="maven-mvnd-${VERSION}-${QUALIFIER}-${MVND_PLATFORM}.zip"
    URL="https://downloads.apache.org/maven/mvnd/${VERSION}/${FILE}"
    RESPONSE="$(curl -s -X POST \
        -H "Consumer-Key: ${SDKMAN_CONSUMER_KEY}" \
        -H "Consumer-Token: ${SDKMAN_CONSUMER_TOKEN}" \
        -H "Content-Type: application/json" \
        -H "Accept: application/json" \
        -d '{"candidate": "mvnd", "version": "'${VERSION}-${QUALIFIER}'", "platform" : "'${SDKMAN_PLATFORM}'", "url": "'${URL}'"}' \
        https://vendors.sdkman.io/release)"

    node -pe "
        var json = JSON.parse(process.argv[1]);
        if (json.status == 201 || json.status == 409) {
            json.status + ' as expected from /release for ${FILE}';
        } else {
            console.log('Unexpected status from /release for ${FILE}: ' + process.argv[1]);
            process.exit(1);
        }
    " "${RESPONSE}"
}

publishRelease ${VERSION} LINUX_64 linux-amd64 m39
publishRelease ${VERSION} MAC_OSX darwin-amd64 m39
publishRelease ${VERSION} MAC_ARM64 darwin-aarch64 m39
publishRelease ${VERSION} WINDOWS_64 windows-amd64 m39
publishRelease ${VERSION} LINUX_64 linux-amd64 m40
publishRelease ${VERSION} MAC_OSX darwin-amd64 m40
publishRelease ${VERSION} MAC_ARM64 darwin-aarch64 m40
publishRelease ${VERSION} WINDOWS_64 windows-amd64 m40

echo "Setting ${VERSION} as a default"
RESPONSE="$(curl -s -X PUT \
    -H "Consumer-Key: ${SDKMAN_CONSUMER_KEY}" \
    -H "Consumer-Token: ${SDKMAN_CONSUMER_TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"candidate": "mvnd", "version": "'${VERSION}-m39'"}' \
    https://vendors.sdkman.io/default)"

node -pe "
    var json = JSON.parse(process.argv[1]);
    if (json.status == 202) {
        json.status + ' as expected from /default';
    } else {
        console.log('Unexpected status from /default: ' + process.argv[1]);
        process.exit(1);
    }
" "${RESPONSE}"

RELEASE_URL="https://downloads.apache.org/maven/mvnd/${VERSION}"
echo "RELEASE_URL = $RELEASE_URL"

RESPONSE="$(curl -s -X POST \
    -H "Consumer-Key: ${SDKMAN_CONSUMER_KEY}" \
    -H "Consumer-Token: ${SDKMAN_CONSUMER_TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"candidate": "mvnd", "version": "'${VERSION}-m39'", "url": "'${RELEASE_URL}'"}' \
    https://vendors.sdkman.io/announce/struct)"

node -pe "
    var json = JSON.parse(process.argv[1]);
    if (json.status == 200 || json.status == 201) {
        json.status + ' as expected from /announce/freeform';
    } else {
        console.log('Unexpected status from /announce/freeform: ' + process.argv[1]);
        process.exit(1);
    }
" "${RESPONSE}"

RESPONSE="$(curl -s -X POST \
    -H "Consumer-Key: ${SDKMAN_CONSUMER_KEY}" \
    -H "Consumer-Token: ${SDKMAN_CONSUMER_TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"candidate": "mvnd", "version": "'${VERSION}-m40'", "url": "'${RELEASE_URL}'"}' \
    https://vendors.sdkman.io/announce/struct)"

node -pe "
    var json = JSON.parse(process.argv[1]);
    if (json.status == 200 || json.status == 201) {
        json.status + ' as expected from /announce/freeform';
    } else {
        console.log('Unexpected status from /announce/freeform: ' + process.argv[1]);
        process.exit(1);
    }
" "${RESPONSE}"
