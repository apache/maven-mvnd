/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.fuse.mvnd.junit;

import java.nio.file.Path;
import java.util.Properties;
import org.jboss.fuse.mvnd.client.DaemonParameters;
import org.jboss.fuse.mvnd.common.Environment;

public class TestParameters extends DaemonParameters {
    static final int TEST_MIN_THREADS = 2;
    private final Path testDir;

    public TestParameters(Path testDir, Path mvndPropertiesPath, Path mavenHome, Path userHome, Path userDir,
            Path multiModuleProjectDirectory,
            Path javaHome, Path localMavenRepository, Path settings, Path logbackConfigurationPath,
            int idleTimeout, int keepAlive, int maxLostKeepAlive) {
        super(new Properties());
        this.testDir = testDir;
        put(Environment.MVND_PROPERTIES_PATH, mvndPropertiesPath);
        put(Environment.MVND_HOME, mavenHome);
        put(Environment.USER_HOME, userHome);
        put(Environment.USER_DIR, userDir);
        put(Environment.MAVEN_MULTIMODULE_PROJECT_DIRECTORY, multiModuleProjectDirectory);
        put(Environment.JAVA_HOME, javaHome);
        put(Environment.MAVEN_REPO_LOCAL, localMavenRepository);
        put(Environment.MAVEN_SETTINGS, settings);
        put(Environment.LOGBACK_CONFIGURATION_FILE, logbackConfigurationPath);
        put(Environment.DAEMON_IDLE_TIMEOUT_MS, idleTimeout);
        put(Environment.DAEMON_KEEP_ALIVE_MS, keepAlive);
        put(Environment.DAEMON_MAX_LOST_KEEP_ALIVE, maxLostKeepAlive);
        put(Environment.MVND_MIN_THREADS, TEST_MIN_THREADS);
    }

    private void put(Environment env, Object value) {
        if (value != null) {
            this.properties.put(env.getProperty(), value.toString());
        }
    }

    public Path getTestDir() {
        return testDir;
    }

}
