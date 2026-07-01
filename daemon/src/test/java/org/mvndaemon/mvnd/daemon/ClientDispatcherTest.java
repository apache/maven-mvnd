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
package org.mvndaemon.mvnd.daemon;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ClientDispatcherTest {
    @Test
    void trimTrailingEols() {
        Assertions.assertEquals(null, ClientDispatcher.trimTrailingEols(null));
        Assertions.assertEquals("foo", ClientDispatcher.trimTrailingEols("foo"));
        Assertions.assertEquals("foo\nbar", ClientDispatcher.trimTrailingEols("foo\nbar"));
        Assertions.assertEquals("foo\nbar", ClientDispatcher.trimTrailingEols("foo\nbar\n"));
        Assertions.assertEquals("foo\nbar", ClientDispatcher.trimTrailingEols("foo\nbar\r\n"));
        Assertions.assertEquals("foo\nbar", ClientDispatcher.trimTrailingEols("foo\nbar\n\r\n"));
        Assertions.assertEquals("", ClientDispatcher.trimTrailingEols("\n"));
    }

    @Test
    void artifactIdDisplayLength() {
        // The display column is sized to the longest artifactId so the goal column stays aligned;
        // a single long name widens the column for every project.

        // Below the floor: the column never shrinks past MIN_ARTIFACT_ID_DISPLAY_LENGTH (20),
        // including the empty reactor.
        Assertions.assertEquals(20, ClientDispatcher.artifactIdDisplayLength(Collections.emptyList()));
        Assertions.assertEquals(20, ClientDispatcher.artifactIdDisplayLength(projects("foo")));
        Assertions.assertEquals(20, ClientDispatcher.artifactIdDisplayLength(projects("a".repeat(20))));

        // Above the floor: round up to the next multiple of 5 strictly greater than the longest name,
        // so an exact multiple still steps up and always leaves a gap before the goal.
        Assertions.assertEquals(25, ClientDispatcher.artifactIdDisplayLength(projects("a".repeat(21))));
        Assertions.assertEquals(25, ClientDispatcher.artifactIdDisplayLength(projects("a".repeat(24))));
        Assertions.assertEquals(30, ClientDispatcher.artifactIdDisplayLength(projects("a".repeat(25))));
        Assertions.assertEquals(30, ClientDispatcher.artifactIdDisplayLength(projects("short", "a".repeat(26), "mid")));
        Assertions.assertEquals(35, ClientDispatcher.artifactIdDisplayLength(projects("a".repeat(30))));
    }

    private static List<MavenProject> projects(String... artifactIds) {
        return Arrays.stream(artifactIds)
                .map(artifactId -> {
                    MavenProject project = new MavenProject();
                    project.setArtifactId(artifactId);
                    return project;
                })
                .collect(Collectors.toList());
    }
}
