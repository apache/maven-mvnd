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

import org.jboss.fuse.mvnd.client.ClientLayout;

public class TestLayout extends ClientLayout {
    private final Path testDir;

    public TestLayout(Path testDir, Path mvndPropertiesPath, Path mavenHome, Path userDir, Path multiModuleProjectDirectory,
            Path javaHome, Path localMavenRepository, Path settings, Path logbackConfigurationPath) {
        super(mvndPropertiesPath, mavenHome, userDir, multiModuleProjectDirectory, javaHome, localMavenRepository,
                settings, logbackConfigurationPath);
        this.testDir = testDir;
    }

    public Path getTestDir() {
        return testDir;
    }

}
