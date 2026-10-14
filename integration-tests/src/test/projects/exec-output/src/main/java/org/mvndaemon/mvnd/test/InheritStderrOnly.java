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
package org.mvndaemon.mvnd.test;

import java.io.File;

/**
 * Forks a JVM that inherits only stderr, so the daemon must pump that one stream to the client.
 */
public class InheritStderrOnly {

    public static void main(String[] args) throws Exception {
        String java = new File(new File(System.getProperty("java.home"), "bin"), "java").getPath();
        String classes = new File(InheritStderrOnly.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI())
                .getPath();
        Process child = new ProcessBuilder(java, "-cp", classes, StderrChild.class.getName())
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        int exit = child.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("Child exited with " + exit);
        }
        // the pump thread forwards the child's output asynchronously
        Thread.sleep(1000);
    }
}
