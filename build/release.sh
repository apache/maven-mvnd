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
export NEXT_VERSION=$2

if [ "${VERSION}x" = "x" ] || [ "${NEXT_VERSION}x" = "x" ]
then
  echo "Specify the version: $0 [version] [next-version]"
  exit 1
fi

# checkout
git checkout master
git fetch origin
git reset --hard origin/master

# update version
mvn versions:set -DnewVersion=$VERSION

# udpate changelog
docker run -it --rm -v "$(pwd)":/usr/local/src/your-app githubchangeloggenerator/github-changelog-generator \
    --user apache --project maven-mvnd --token $GITHUB_TOKEN --future-release $VERSION --exclude-tags early-access

# rebuild native libraries
pushd native
make native-all
popd

# commit
git add -A
git commit -m "[release] Release $VERSION"

# Create and push tag
git tag $VERSION
git push origin $VERSION
# Pushing a tag will trigger the CI to build the release and publish
# the artifacts on https://github.com/apache/maven-mvnd/releases

# update version
mvn versions:set -DnewVersion=$NEXT_VERSION

# commit
git add -A
git commit -m "Next is $NEXT_VERSION"
git push origin master
