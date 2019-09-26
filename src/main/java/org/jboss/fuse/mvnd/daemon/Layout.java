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
package org.jboss.fuse.mvnd.daemon;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Layout {

    private static final Path MAVEN_HOME = Paths.get(System.getProperty("maven.home")).toAbsolutePath().normalize();

    private static final Path JAVA_HOME = Paths.get(System.getProperty("java.home")).toAbsolutePath().normalize();

    public static Path javaHome() {
        return JAVA_HOME;
    }

    public static Path mavenHome() {
        return MAVEN_HOME;
    }

    public static Path registry() {
        return mavenHome().resolve("daemon/registry.bin");
    }

    public static Path daemonLog(String daemon) {
        return mavenHome().resolve("daemon/daemon-" + daemon + ".log");
    }
}
