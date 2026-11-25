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


set -e
export VERSION=$1

if [ "${VERSION}x" = "x" ]
then
  echo "Specify the version: $0 [version]"
  exit 1
fi

DIST_DEV_URL="https://dist.apache.org/repos/dist/dev/maven/mvnd/${VERSION}"
DIST_RELEASE_URL="https://dist.apache.org/repos/dist/release/maven/mvnd/${VERSION}"

if svn info "${DIST_DEV_URL}" > /dev/null 2>&1
then
  # release candidate was staged in dist/dev by release-candidate.sh
  svn move -m "Release Apache Maven Daemon ${VERSION}" \
    "${DIST_DEV_URL}" \
    https://dist.apache.org/repos/dist/release/maven/mvnd/
elif svn info "${DIST_RELEASE_URL}" > /dev/null 2>&1
then
  # release candidate was staged in ATR by release-candidate-atr.sh,
  # ATR has already published it to dist/release
  echo "${VERSION} not found in dist/dev, but already in dist/release (released by ATR), skipping svn move"
else
  echo "${VERSION} not found in ${DIST_DEV_URL} nor in ${DIST_RELEASE_URL}"
  echo "If the release candidate was staged in ATR, finish the release on https://releases.apache.org/projects/maven-mvnd first"
  exit 1
fi

./build/publish-on-homebrew.sh ${VERSION}
./build/publish-on-sdkman.sh ${VERSION}
# TODO: publish on macports
# TODO: publish on chocolatey
# TODO: publish on scoop