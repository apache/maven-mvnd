/*
 * Copyright 2021 the original author or authors.
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
package org.mvndaemon.mvnd.plugin.fastclean;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Random;
import java.util.stream.Stream;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Extracts JavaDoc blocks from enum entries and stores them into a properties file.
 */
@Mojo(name = "clean", defaultPhase = LifecyclePhase.CLEAN, threadSafe = true, requiresProject = true, requiresDependencyResolution = ResolutionScope.NONE)
public class FastCleanMojo extends AbstractMojo {

    /**
     * This is where build results go.
     */
    @Parameter(defaultValue = "${project.build.directory}", readonly = true, required = true)
    private File dir;

    /**
     * Sets whether the plugin runs in verbose mode. As of plugin version 2.3, the default value is derived from Maven's
     * global debug flag (compare command line switch <code>-X</code>). <br/>
     * Starting with <b>3.0.0</b> the property has been renamed from <code>clean.verbose</code> to
     * <code>maven.clean.verbose</code>.
     */
    @Parameter(property = "maven.clean.verbose")
    private Boolean verbose;

    /**
     * Disables the plugin execution. <br/>
     */
    @Parameter(property = "maven.clean.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Indicates whether the build will continue even if there are clean errors.
     */
    @Parameter(property = "maven.clean.failOnError", defaultValue = "true")
    private boolean failOnError;

    /**
     * Indicates whether the plugin should undertake additional attempts (after a short delay) to delete a file if the
     * first attempt failed. This is meant to help deleting files that are temporarily locked by third-party tools like
     * virus scanners or search indexing.
     */
    @Parameter(property = "maven.clean.retryOnError", defaultValue = "true")
    private boolean retryOnError;

    @Override
    public void execute() throws MojoFailureException {
        final Log log = getLog();
        if (skip) {
            log.info(getClass().getSimpleName() + " skipped per skip parameter");
            return;
        }

        Path directory = dir.toPath();
        if (Files.exists(directory)) {
            if (Files.isDirectory(directory)) {
                String random = Long.toHexString(new Random().nextLong());
                String name = directory.getFileName().toString() + "-" + random;
                Path tmpDir = directory.resolveSibling(name);
                Path delDir = directory.resolve(name);
                try {
                    Files.move(directory, tmpDir);
                    Files.createDirectory(directory);
                    Files.move(tmpDir, delDir);
                } catch (IOException e) {
                    error("Unable to move " + directory, e);
                }
                new Thread(() -> deleteDir(delDir, retryOnError)).run();
            } else {
                error("The path " + directory + " is not a directory", null);
            }
        }
    }

    private void error(String message, Throwable cause) throws MojoFailureException {
        if (failOnError) {
            if (cause == null) {
                throw new MojoFailureException(message);
            } else {
                throw new MojoFailureException(message, cause);
            }
        } else {
            getLog().warn(message);
        }
    }

    public void deleteDir(Path dir, boolean retryOnError) {
        if (Files.exists(dir)) {
            boolean result = false;
            try (Stream<Path> files = Files.walk(dir)) {
                result = files.sorted(Comparator.reverseOrder())
                        .map(this::deleteFile)
                        .allMatch(b -> b);
            } catch (IOException e) {
                // ignore
            }
            if (!result) {
                if (retryOnError) {
                    deleteDir(dir, false);
                } else {
                    getLog().warn("Error deleting directory " + dir);
                }
            }
        }
    }

    private boolean deleteFile(Path f) {
        try {
            Files.delete(f);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

}
