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
export BRANCH=mvnd-0.9.x

if [ "${VERSION}x" = "x" ] || [ "${NEXT_VERSION}x" = "x" ]
then
  echo "Specify the version: $0 [version] [next-version]"
  exit 1
fi

startup_check()
{
  # Need to do a git fecth first to download remote changes for us to compare against
  git fetch
  # Most of this code was taken from the __git_ps1_changes method of https://github.com/markgandolfo/git-bash-completion
  local branch_ref
  branch_ref=$(git symbolic-ref -q HEAD 2>/dev/null)
  if [ -n "$branch_ref" ]
  then
    local branch_origin
    branch_origin=$(git for-each-ref --format='%(upstream:short)' $branch_ref)
    if [ -n "$branch_origin" ]
    then
      local branch
      branch=${branch_ref##refs/heads/}

      if [ "$branch" != "$BRANCH" ]
      then
        echo "Not working on the $BRANCH - cannot proceed"
        exit 1
      fi

      local unpush
      unpush=$(git rev-list $branch_origin..$branch --count)
      local unpull
      unpull=$(git rev-list $branch..$branch_origin --count)
      local staged
      staged=$(git diff --staged --name-status | wc -l)
      local uncommits
      uncommits=$(git status -s -uall --porcelain)

      if [[ $unpush -gt 0 ]]; then
        echo "There are changes which have not been pushed - cannot proceed. The following commits need to be pushed:"
        local unpushed
        unpushed=$(git rev-list $branch_origin..$branch)
        for commit in $unpushed; do
          git --no-pager log --pretty=format:"%H - %an, %ar : %s" -n 1 $commit
        done
        exit 1
      fi

      if [[ $unpull -gt 0 ]]; then
        echo "There are changes which have not been pulled - cannot proceed. The following commits have been added to $BRANCH since your last pull:"
        local unpulled
        unpulled=$(git rev-list $branch..$branch_origin)
        for commit in $unpulled; do
          git --no-pager log --pretty=format:"%H - %an, %ar : %s" -n 1 $commit
        done
        exit 1
      fi

      if [[ $staged -gt 0 ]]; then
        local staging
        staging=$(git diff --staged --name-status)
        echo "There are changes which are staged but have been commited - cannot proceed"
        echo $staging
        exit 1
      fi

      local unstaged
      unstaged=$(echo "$uncommits" | grep -c "^ [A-Z]") || true
      if [[ $unstaged -gt 0 ]]; then
        echo "There are unstaged changes - cannot proceed"
        echo $(echo "$uncommits" | grep "^ [A-Z]")
        exit 1
      fi

      local untracked
      untracked=$(echo "$uncommits" | grep -c "^??") || true
      if [[ $untracked -gt 0 ]]; then
        echo "There are untracked changes - cannot proceed"
        echo $(echo "$uncommits" | grep "^??")
        exit 1
      fi
    fi
  else
    echo "Working folder isn't a git folder"
    exit 1
  fi
}

# check
startup_check

# update version
mvn versions:set -DnewVersion=$VERSION

# udpate changelog
docker run -it --rm -v "$(pwd)":/usr/local/src/your-app githubchangeloggenerator/github-changelog-generator \
    --user apache --project maven-mvnd --token $GITHUB_TOKEN --future-release $VERSION --exclude-tags early-access,1.0.0-m1

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
git push origin $BRANCH
