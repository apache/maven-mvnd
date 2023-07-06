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
package org.mvndaemon.mvnd.junit;

import java.nio.file.Path;
import java.time.Duration;

import org.mvndaemon.mvnd.client.DaemonParameters;
import org.mvndaemon.mvnd.common.Environment;
import org.mvndaemon.mvnd.common.TimeUtils;

public class TestParameters extends DaemonParameters {
    static final int TEST_MIN_THREADS = 2;
    private final Path testDir;
    private final boolean noTransferProgress;

    public TestParameters(
            Path testDir,
            Path mvndPropertiesPath,
            Path mavenHome,
            Path userHome,
            Path userDir,
            Path multiModuleProjectDirectory,
            Path javaHome,
            Path localMavenRepository,
            Path settings,
            Duration idleTimeout,
            Duration keepAlive,
            int maxLostKeepAlive,
            int minThreads,
            boolean noTransferProgress) {
        super(new PropertiesBuilder()
                .put(Environment.MVND_PROPERTIES_PATH, mvndPropertiesPath)
                .put(Environment.MVND_HOME, mavenHome)
                .put(Environment.USER_HOME, userHome)
                .put(Environment.USER_DIR, userDir)
                .put(Environment.MAVEN_MULTIMODULE_PROJECT_DIRECTORY, multiModuleProjectDirectory)
                .put(Environment.JAVA_HOME, javaHome)
                .put(Environment.MAVEN_REPO_LOCAL, localMavenRepository)
                .put(Environment.MAVEN_SETTINGS, settings)
                .put(Environment.MVND_IDLE_TIMEOUT, TimeUtils.printDuration(idleTimeout))
                .put(Environment.MVND_KEEP_ALIVE, TimeUtils.printDuration(keepAlive))
                .put(Environment.MVND_MAX_LOST_KEEP_ALIVE, maxLostKeepAlive)
                .put(Environment.MVND_MIN_THREADS, minThreads));
        this.testDir = testDir;
        this.noTransferProgress = noTransferProgress;
    }

    public DaemonParameters clearMavenMultiModuleProjectDirectory() {
        return derive(b -> b.put(Environment.MAVEN_MULTIMODULE_PROJECT_DIRECTORY, null));
    }

    public DaemonParameters withMavenMultiModuleProjectDirectory(Path dir) {
        return derive(b -> b.put(Environment.MAVEN_MULTIMODULE_PROJECT_DIRECTORY, dir.toString()));
    }

    public TestParameters withTransferProgress() {
        return new TestParameters(
                testDir,
                value(Environment.MVND_PROPERTIES_PATH).asPath(),
                value(Environment.MVND_HOME).asPath(),
                value(Environment.USER_HOME).asPath(),
                value(Environment.USER_DIR).asPath(),
                value(Environment.MAVEN_MULTIMODULE_PROJECT_DIRECTORY).asPath(),
                value(Environment.JAVA_HOME).asPath(),
                value(Environment.MAVEN_REPO_LOCAL).asPath(),
                value(Environment.MAVEN_SETTINGS).asPath(),
                value(Environment.MVND_IDLE_TIMEOUT).asDuration(),
                value(Environment.MVND_KEEP_ALIVE).asDuration(),
                value(Environment.MVND_MAX_LOST_KEEP_ALIVE).asInt(),
                value(Environment.MVND_MIN_THREADS).asInt(),
                false);
    }

    public Path getTestDir() {
        return testDir;
    }

    public boolean isNoTransferProgress() {
        return noTransferProgress;
    }
}
