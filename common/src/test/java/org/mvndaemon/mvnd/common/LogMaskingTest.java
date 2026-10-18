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
package org.mvndaemon.mvnd.common;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogMaskingTest {

    @Test
    void masksSensitiveDefinitions() {
        assertEquals(
                Arrays.asList(
                        "verify",
                        "-Dgpg.passphrase=****",
                        "-DskipTests=true",
                        "-D",
                        "repo.password=****",
                        "--define",
                        "api.TOKEN=****",
                        "-Dflag"),
                LogMasking.args(Arrays.asList(
                        "verify",
                        "-Dgpg.passphrase=hunter2",
                        "-DskipTests=true",
                        "-D",
                        "repo.password=hunter2",
                        "--define",
                        "api.TOKEN=abc",
                        "-Dflag")));
    }

    @Test
    void masksSensitiveEnvironmentValues() {
        assertEquals("****", LogMasking.value("NPM_TOKEN", "abc"));
        assertEquals("/usr/bin", LogMasking.value("PATH", "/usr/bin"));
        assertEquals(null, LogMasking.value("DB_PASSWORD", null));
    }

    @Test
    void messagesDoNotPrintUserInput() {
        Message.Prompt prompt = new Message.Prompt("p", "u1", "Password:", true);
        String response = prompt.response("hunter2").toString();
        assertFalse(response.contains("hunter2"), response);
        assertTrue(response.contains("[7 chars]"), response);

        String input = Message.inputResponse("hunter2").toString();
        assertFalse(input.contains("hunter2"), input);

        String request = new Message.BuildRequest(
                        Arrays.asList("-Dgpg.passphrase=hunter2", "verify"), "/w", "/w", Collections.emptyMap())
                .toString();
        assertFalse(request.contains("hunter2"), request);
    }
}
