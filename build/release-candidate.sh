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

#
# TODO: if multiple runs are available on the same tag, the artifactsUrl variable will contain several urls:
# TODO:   artifactsUrl='/repos/apache/maven-mvnd/actions/runs/2245126309/artifacts /repos/apache/maven-mvnd/actions/runs/2245048166/artifacts /repos/apache/maven-mvnd/actions/runs/2235462006/artifacts'
# TODO: the script should detect and print a meaningful error instead of a cryptic failure:
# TODO:   accepts 1 arg(s), received 3
# TODO:   Downloading artifacts from
# TODO:   accepts 1 arg(s), received 0
#

# Enable for debug execution
# set -x

set -e
export VERSION=$1

if [ "${VERSION}x" = "x" ]
then
  echo "Specify the version: $0 [version]"
  exit 1
fi

rm -Rf target/releases/mvnd/${VERSION}
mkdir -p target/releases/mvnd/${VERSION}
pushd target/releases/mvnd/${VERSION}

gh release download ${VERSION}

for dist in maven-mvnd*.*
do
  echo "Processing ${dist}"
  # sha256 are used by homebrew which does not support sha512 atm
  shasum -a 256 -b ${dist} | cut -d ' ' -f 1 > ${dist}.sha256
  shasum -a 512 -b ${dist} | cut -d ' ' -f 1 > ${dist}.sha512
  gpg --detach-sign --armor ${dist}
done

cd ..
svn co https://dist.apache.org/repos/dist/dev/maven/mvnd .
svn add ${VERSION}
svn commit -m "Release Apache Maven Daemon ${VERSION}"

popd
