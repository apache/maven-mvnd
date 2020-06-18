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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.function.Supplier;

public class Layout {

    private static Layout ENV_INSTANCE;

    private final Path mavenHome;
    private final Path userDir;
    private final Path multiModuleProjectDirectory;
    private final Path mvndPropertiesPath;

    public Layout(Path mvndPropertiesPath, Path mavenHome, Path userDir, Path multiModuleProjectDirectory) {
        super();
        this.mvndPropertiesPath = mvndPropertiesPath;
        this.mavenHome = mavenHome;
        this.userDir = userDir;
        this.multiModuleProjectDirectory = multiModuleProjectDirectory;
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

    public Path getMvndPropertiesPath() {
        return mvndPropertiesPath;
    }

    public static Layout getEnvInstance() {
        if (ENV_INSTANCE == null) {
            final Path mvndPropertiesPath = Environment.findMvndPropertiesPath();
            final Supplier<Properties> mvndProperties = lazyMvndProperties(mvndPropertiesPath);
            final Path pwd = Paths.get(".").toAbsolutePath().normalize();

            ENV_INSTANCE = new Layout(
                    mvndPropertiesPath,
                    Environment.findMavenHome(mvndProperties, mvndPropertiesPath),
                    pwd,
                    Environment.findMultiModuleProjectDirectory(pwd));
        }
        return ENV_INSTANCE;
    }

    static Supplier<Properties> lazyMvndProperties(Path mvndPropertiesPath) {
        return new Supplier<Properties>() {

            private volatile Properties properties;

            @Override
            public Properties get() {
                Properties result = this.properties;
                if (result == null) {
                    result = new Properties();
                    if (Files.exists(mvndPropertiesPath)) {
                        try (InputStream in = Files.newInputStream(mvndPropertiesPath)) {
                            result.load(in);
                        } catch (IOException e) {
                            throw new RuntimeException("Could not read " + mvndPropertiesPath);
                        }
                    }
                    this.properties = result;
                }
                return result;
            }
        };
    }

    @Override
    public String toString() {
        return "Layout [mavenHome=" + mavenHome + ", userDir=" + userDir + ", multiModuleProjectDirectory="
                + multiModuleProjectDirectory + "]";
    }

}
