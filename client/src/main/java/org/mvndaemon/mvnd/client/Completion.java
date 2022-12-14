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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Completion {

    public static String getCompletion(String shell, DaemonParameters daemonParameters) {
        if (!"bash".equals(shell)) {
            throw new IllegalArgumentException("Unexpected --completion value: '" + shell + "'; expected: 'bash'");
        }
        final Path bashCompletionPath = daemonParameters.mvndHome().resolve("bin/mvnd-bash-completion.bash");
        if (!Files.isRegularFile(bashCompletionPath)) {
            throw new IllegalStateException("Bash completion file does not exist: " + bashCompletionPath);
        }
        try {
            return new String(Files.readAllBytes(bashCompletionPath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + bashCompletionPath, e);
        }
    }
}
