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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides the {@code MAVEN_CMD_LINE_ARGS} environment variable that the {@code mvn} script exports.
 */
final class MavenCmdLineArgs {

    private static final String NAME = "MAVEN_CMD_LINE_ARGS";

    private MavenCmdLineArgs() {}

    /**
     * Adds {@code MAVEN_CMD_LINE_ARGS} to {@code env}, as the {@code mvn} script does with
     * {@code MAVEN_CMD_LINE_ARGS="$MAVEN_CONFIG $@"}, so that extensions can read the command line.
     * A value already present in {@code env} is kept.
     */
    static Map<String, String> addTo(Map<String, String> env, List<String> args) {
        if (env.containsKey(NAME)) {
            return env;
        }
        Map<String, String> result = new HashMap<>(env);
        String mavenConfig = env.get("MAVEN_CONFIG");
        result.put(NAME, (mavenConfig != null ? mavenConfig : "") + " " + String.join(" ", args));
        return result;
    }
}
