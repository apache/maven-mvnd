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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DistroIT {

    /**
     * Asserts that we do not have the same libs in lib/ext and in lib or boot directories.
     */
    @Test
    void noDuplicateJars() {
        String property = System.getProperty("mvnd.home");
        assertNotNull(property, "mvnd.home must be defined");
        final Path mavenHome = Paths.get(property);
        List<Avc> avcs = listJars(mavenHome);
        Map<String, List<Avc>> avcsByArtifactId = avcs.stream().collect(Collectors.groupingBy(Avc::getArtifactId));
        List<List<Avc>> duplicateJars = avcsByArtifactId.values().stream()
                .filter(list -> list.size() > 1)
                .collect(Collectors.toList());

        Assertions.assertTrue(duplicateJars.isEmpty(), mavenHome + " contains duplicates jars" + duplicateJars);
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

    private static List<Avc> listJars(Path mavenHome) {
        try (Stream<Path> stream = Files.walk(mavenHome)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .map(Avc::of)
                    .collect(Collectors.toList());
        } catch (java.io.IOException e) {
            throw new RuntimeException("Could not list " + mavenHome, e);
        }
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

        public String getArtifactId() {
            return artifactId;
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
