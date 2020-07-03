package org.jboss.fuse.mvnd.test.module.plugin.mojo;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 */
@Mojo(name = "hello", requiresProject = true)
public class HelloMojo extends AbstractMojo {

    @Parameter
    File file;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        try {
            final Path path = file.toPath();
            Files.createDirectories(path.getParent());
            Files.write(path, "Hello".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("", e);
        }

    }

}
