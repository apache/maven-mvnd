package org.jboss.fuse.mvnd.junit;

import java.nio.file.Path;

import org.jboss.fuse.mvnd.client.ClientLayout;

public class TestLayout extends ClientLayout {
    private final Path testDir;

    public TestLayout(Path testDir, Path mvndPropertiesPath, Path mavenHome, Path userDir, Path multiModuleProjectDirectory,
            Path javaHome,
            Path localMavenRepository, Path settings) {
        super(mvndPropertiesPath, mavenHome, userDir, multiModuleProjectDirectory, javaHome, localMavenRepository, settings);
        this.testDir = testDir;
    }

    public Path getTestDir() {
        return testDir;
    }

}
