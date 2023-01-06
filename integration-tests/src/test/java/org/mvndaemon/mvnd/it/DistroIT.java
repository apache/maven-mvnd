/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.mvndaemon.mvnd.it;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DistroIT {

    /**
     * Asserts that we do not have the same libs in lib/ext and in lib or boot directories.
     */
    @Test
    void noDuplicateJars() {
        final Path mavenHome = Paths.get(System.getProperty("mvnd.home"));
        Set<Avc> mavenLibs =
                streamJars(mavenHome, "mvn/lib", "mvn/boot").collect(Collectors.toCollection(TreeSet::new));
        Assertions.assertFalse(mavenLibs.isEmpty());
        final List<Avc> mvndJars = streamJars(mavenHome, "mvn/lib/ext").collect(Collectors.toList());
        Assertions.assertFalse(mvndJars.isEmpty());

        final List<Avc> dups = mvndJars.stream()
                .filter(avc -> mavenLibs.stream().anyMatch(mvnAvc -> mvnAvc.sameArtifactId(avc)))
                .collect(Collectors.toList());

        final String msg = mavenHome.resolve("mvn/lib/ext") + " contains duplicates available in "
                + mavenHome.resolve("mvn/lib") + " or " + mavenHome.resolve("mvn/boot");
        Assertions.assertEquals(new ArrayList<Avc>(), dups, msg);
    }

    @Test
    void avcOf() {
        assertAvcOf("foo-bar-1.2.3.jar", "foo-bar", "1.2.3", null);
        assertAvcOf("foo_bar-1.2.3-classifier.jar", "foo_bar", "1.2.3", "classifier");
    }

    void assertAvcOf(String jarName, String artifactId, String version, String classifier) {
        Avc avc = Avc.of(jarName);
        Assertions.assertEquals(artifactId, avc.artifactId, "artifactId in " + jarName);
        Assertions.assertEquals(version, avc.version, "version in " + jarName);
        Assertions.assertEquals(classifier, avc.classifier, "classifier in " + jarName);
    }

    private static Stream<Avc> streamJars(Path mavenHome, String... dirs) {
        return Stream.of(dirs)
                .map(mavenHome::resolve)
                .flatMap((Path p) -> {
                    try {
                        return Files.list(p);
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("Could not list " + p, e);
                    }
                })
                .filter(p -> p.getFileName().toString().endsWith(".jar"))
                .filter(Files::isRegularFile)
                .map(Path::getFileName)
                .map(Path::toString)
                .map(Avc::of);
    }

    static class Avc implements Comparable<Avc> {

        private static final Pattern JAR_NAME_PATTERN =
                Pattern.compile("^(.*)(?:-([0-9]+(?:\\.[0-9]+)*))(?:-(.*))?.jar$");

        public static Avc of(String jarName) {
            final Matcher m = JAR_NAME_PATTERN.matcher(jarName);
            if (m.find()) {
                return new Avc(m.group(1), m.group(2), m.group(3), jarName);
            } else {
                throw new IllegalStateException(
                        "Jar name " + jarName + " does not match " + JAR_NAME_PATTERN.pattern());
            }
        }

        private final String artifactId;
        private final String version;
        private final String classifier;
        private final String jarName;

        public Avc(String artifactId, String version, String classifier, String jarName) {
            this.artifactId = artifactId;
            this.version = version;
            this.classifier = classifier;
            this.jarName = jarName;
        }

        @Override
        public String toString() {
            return jarName;
        }

        @Override
        public int hashCode() {
            return jarName.hashCode();
        }

        public boolean sameArtifactId(Avc other) {
            return this.artifactId.equals(other.artifactId);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            Avc other = (Avc) obj;
            return this.jarName.equals(other.jarName);
        }

        @Override
        public int compareTo(Avc other) {
            return this.jarName.compareTo(other.jarName);
        }
    }
}
