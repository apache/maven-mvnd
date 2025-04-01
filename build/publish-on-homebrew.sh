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
export VERSION=$1

if [ "${VERSION}x" = "x" ]
then
  echo "Specify the version: $0 [version]"
  exit 1
fi

rm -Rf target/releases/${VERSION}
mkdir -p target/releases/${VERSION}
pushd target/releases

darwinAmdZipUrl="https://downloads.apache.org/maven/mvnd/${VERSION}/maven-mvnd-${VERSION}-darwin-amd64.zip"
darwinAmdSha256="$(curl -L --silent "${darwinAmdZipUrl}.sha256")"
darwinArmZipUrl="https://downloads.apache.org/maven/mvnd/${VERSION}/maven-mvnd-${VERSION}-darwin-aarch64.zip"
darwinArmSha256="$(curl -L --silent "${darwinArmZipUrl}.sha256")"
linuxZipUrl="https://downloads.apache.org/maven/mvnd/${VERSION}/maven-mvnd-${VERSION}-linux-amd64.zip"
linuxSha256="$(curl -L --silent "${linuxZipUrl}.sha256")"
linuxArmZipUrl="https://downloads.apache.org/maven/mvnd/${VERSION}/maven-mvnd-${VERSION}-linux-aarch64.zip"
linuxArmSha256="$(curl -L --silent "${linuxArmZipUrl}.sha256")"

echo "Updating Formula/mvnd.rb with"
echo "version: ${VERSION}"
echo "darwin-amd-url: ${darwinAmdZipUrl}"
echo "darwin-amd-sha256: ${darwinAmdSha256}"
echo "darwin-arm-url: ${darwinArmZipUrl}"
echo "darwin-arm-sha256: ${darwinArmSha256}"
echo "linux-url: ${linuxZipUrl}"
echo "linux-sha256: ${linuxSha256}"
echo "linux-arm-url: ${linuxArmZipUrl}"
echo "linux-arm-sha256: ${linuxArmSha256}"

rm -Rf homebrew-mvnd
git clone https://github.com/mvndaemon/homebrew-mvnd.git
cd homebrew-mvnd

perl -i -0pe 's|(on_macos do[\s\S\n]+on_intel do\n\s+url )\"([^\"]+)\"(\n\s+sha256 )\"([^\"]+)\"|$1\"'${darwinAmdZipUrl}'\"$3\"'${darwinAmdSha256}'\"|g' Formula/mvnd.rb
perl -i -0pe 's|(on_macos do[\s\S\n]+on_arm do\n\s+url )\"([^\"]+)\"(\n\s+sha256 )\"([^\"]+)\"|$1\"'${darwinArmZipUrl}'\"$3\"'${darwinArmSha256}'\"|g' Formula/mvnd.rb
perl -i -0pe 's|(on_linux do[\s\S\n]+on_intel do\n\s+url )\"([^\"]+)\"(\n\s+sha256 )\"([^\"]+)\"|$1\"'${linuxZipUrl}'\"$3\"'${linuxSha256}'\"|g' Formula/mvnd.rb
perl -i -0pe 's|(on_linux do[\s\S\n]+on_arm do\n\s+url )\"([^\"]+)\"(\n\s+sha256 )\"([^\"]+)\"|$1\"'${linuxArmZipUrl}'\"$3\"'${linuxArmSha256}'\"|g' Formula/mvnd.rb
perl -i -0pe 's|(version )"([^\"]+)"|$1\"'${VERSION}'\"|g' Formula/mvnd.rb

if [ -n "$(git status --porcelain)" ]; then
    echo "Committing release ${VERSION}"
    git config --global user.email "gnodet@gmail.com"
    git config --global user.name "Guillaume Nodet"
    git add -A
    git commit -m "Release ${VERSION}"
    git push origin master
else
    echo "Nothing to commit"
fi

popd