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
package org.mvndaemon.mvnd.client;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MavenCmdLineArgsTest {

    @Test
    void mavenCmdLineArgsFollowsTheMvnScript() {
        Map<String, String> env =
                MavenCmdLineArgs.addTo(new HashMap<>(), Arrays.asList("clean", "install", "-Dversioning.disable=true"));
        assertEquals(" clean install -Dversioning.disable=true", env.get("MAVEN_CMD_LINE_ARGS"));

        Map<String, String> base = new HashMap<>();
        base.put("MAVEN_CONFIG", "-T4");
        env = MavenCmdLineArgs.addTo(base, Arrays.asList("verify"));
        assertEquals("-T4 verify", env.get("MAVEN_CMD_LINE_ARGS"));
    }

    @Test
    void mavenCmdLineArgsSetByUserIsNotOverridden() {
        Map<String, String> base = new HashMap<>();
        base.put("MAVEN_CMD_LINE_ARGS", "custom");
        Map<String, String> env = MavenCmdLineArgs.addTo(base, Arrays.asList("clean"));
        assertEquals("custom", env.get("MAVEN_CMD_LINE_ARGS"));
    }
}
