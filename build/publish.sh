#!/usr/bin/env bash
#
# Copyright 2022 the original author or authors.
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
set -x
export VERSION=$1

if [ "${VERSION}x" = "x" ]
then
  echo "Specify the version: $0 [version]"
  exit 1
fi

rm -Rf target/releases/${VERSION}
mkdir -p target/releases/${VERSION}
pushd target/releases

runsUrl=$(echo "https://api.github.com/repos/apache/maven-mvnd/actions/runs" | sed -e 's?https://api.github.com??g')
artifactsUrl=$(gh api -H "Accept: application/vnd.github.v3+json" $runsUrl --jq '.workflow_runs[] | select(.name=="Release" and .head_branch=="'${VERSION}'") | .artifacts_url' | sed -e 's?https://api.github.com??g')
downloadUrl=$(gh api -H "Accept: application/vnd.github.v3+json" $artifactsUrl --jq '.artifacts[] | select(.name = "artifacts") | .archive_download_url' | sed -e 's?https://api.github.com??g')
echo "Downloading artifacts from $downloadUrl"
gh api $downloadUrl > artifacts-${VERSION}.zip
unzip artifacts-${VERSION}.zip -d ${VERSION}
cd ${VERSION}

for dist in darwin-amd64.zip linux-amd64.zip windows-amd64.zip src.zip src.tar.gz
do
  FILE=mvnd-${VERSION}-${dist}
  echo "$(cat ${FILE}.sha256) ${FILE}" | shasum -c
  md5 -q ${FILE} > ${FILE}.md5
  shasum -a 1 -b ${FILE} | cut -d ' ' -f 1 > ${FILE}.sha1
  shasum -a 512 -b ${FILE} | cut -d ' ' -f 1 > ${FILE}.sha512
  gpg --detach-sign --armor ${FILE}
done

svn co https://dist.apache.org/repos/dist/dev/maven/mvnd
mv ${VERSION} mvnd
cd mvnd
svn add ${VERSION}
svn commit -m "Release Apache Maven Daemon ${VERSION}"

popd
