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
package org.jboss.fuse.mvnd.client;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class Layout {

    private static Layout ENV_INSTANCE;

    private final Path javaHome;
    private final Path mavenHome;
    private final Path userDir;
    private final Path multiModuleProjectDirectory;

    public Layout(Path javaHome, Path mavenHome, Path userDir, Path multiModuleProjectDirectory) {
        super();
        this.javaHome = javaHome;
        this.mavenHome = mavenHome;
        this.userDir = userDir;
        this.multiModuleProjectDirectory = multiModuleProjectDirectory;
    }

    public Path javaHome() {
        return javaHome;
    }

    public Path mavenHome() {
        return mavenHome;
    }

    public Path userDir() {
        return userDir;
    }

    public Path registry() {
        return mavenHome.resolve("daemon/registry.bin");
    }

    public Path daemonLog(String daemon) {
        return mavenHome.resolve("daemon/daemon-" + daemon + ".log");
    }

    public Path multiModuleProjectDirectory() {
        return multiModuleProjectDirectory;
    }

    private static String getProperty(String key) {
        return Objects.requireNonNull(System.getProperty(key), "Undefined system property: " + key);
    }

    public static Layout getEnvInstance() {
        if (ENV_INSTANCE == null) {
            ENV_INSTANCE = new Layout(Paths.get(getProperty("java.home")).toAbsolutePath().normalize(),
                    Paths.get(getProperty("maven.home")).toAbsolutePath().normalize(),
                    Paths.get(getProperty("user.dir")).toAbsolutePath().normalize(),
                    Paths.get(getProperty("maven.multiModuleProjectDirectory")).toAbsolutePath().normalize());
        }
        return ENV_INSTANCE;
    }

}
