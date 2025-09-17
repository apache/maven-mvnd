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
package org.mvndaemon.mvnd.plugin.doc;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.JavaDocSource;
import org.jboss.forge.roaster.model.source.JavaEnumSource;

/**
 * Extracts JavaDoc blocks from enum entries and stores them into a properties file.
 */
@Mojo(
        name = "doc",
        defaultPhase = LifecyclePhase.NONE,
        threadSafe = true,
        requiresProject = true,
        requiresDependencyResolution = ResolutionScope.NONE)
public class DocMojo extends AbstractMojo {

    /**
     * The current project's <code>${basedir}</code>
     */
    @Parameter(readonly = true, defaultValue = "${project.basedir}")
    File baseDir;

    /** A list of fully qualified enum names to process. */
    @Parameter(defaultValue = "org.mvndaemon.mvnd.common.Environment,org.mvndaemon.mvnd.common.OptionType")
    String[] enums;

    /**
     * If {@code true} the execution of this mojo will be skipped altogether; otherwise this mojo will be executed.
     */
    @Parameter(defaultValue = "false", property = "mvnd.build.doc.skip")
    boolean skip;

    @Override
    @SuppressWarnings("deprecation")
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info(getClass().getSimpleName() + " skipped per skip parameter");
            return;
        }

        final Path basePath = baseDir.toPath();

        for (String enumClassName : enums) {
            extractEnumJavaDoc(basePath, enumClassName);
        }
    }

    static void extractEnumJavaDoc(Path basePath, String enumClassName) throws MojoFailureException {
        final String classRelPath = enumClassName.replace('.', '/');
        final Path enumClassLocation = basePath.resolve("src/main/java").resolve(classRelPath + ".java");
        final Path propsPath = basePath.resolve("target/classes/" + classRelPath + ".javadoc.properties");
        try {
            Files.createDirectories(propsPath.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("Could not create " + propsPath.getParent(), e);
        }

        if (!Files.isRegularFile(enumClassLocation)) {
            throw new IllegalStateException(enumClassLocation + " does not exist: ");
        }

        try {
            final JavaEnumSource source = Roaster.parse(JavaEnumSource.class, enumClassLocation.toFile());

            final Properties optionsProperties = new SortedProperties();
            for (EnumConstantSource enumConst : source.getEnumConstants()) {
                final JavaDocSource<EnumConstantSource> javaDoc = enumConst.getJavaDoc();
                final String javadocText = javaDoc.getText().replaceAll("&#47;", "/");
                optionsProperties.setProperty(enumConst.getName(), javadocText);
            }
            optionsProperties.store(Files.newOutputStream(propsPath), null);
        } catch (IOException e) {
            throw new MojoFailureException("Could not parse " + enumClassLocation, e);
        }
    }

    /**
     * A {@link Properties} with a binarily reproducible {@code store()} operation.
     */
    static class SortedProperties extends Properties {
        private static final long serialVersionUID = 5983297690254771479L;

        @Override
        public synchronized Enumeration<Object> keys() {
            final Iterator<Object> it = new TreeSet<>(keySet()).iterator();
            return new Enumeration<>() {
                public boolean hasMoreElements() {
                    return it.hasNext();
                }

                public Object nextElement() {
                    return it.next();
                }
            };
        }

        public Set<Map.Entry<Object, Object>> entrySet() {
            Comparator<Map.Entry<Object, Object>> comparator = Comparator.comparing(e -> (Comparable) e.getKey());
            final Set<Map.Entry<Object, Object>> result = new TreeSet<>(comparator);
            result.addAll(super.entrySet());
            return result;
        }

        @Override
        public void store(Writer writer, String comments) throws IOException {
            super.store(new SkipFirstLineBufferedWriter(writer), null);
        }

        @Override
        public void store(OutputStream out, String comments) throws IOException {
            this.store(new OutputStreamWriter(out, "8859_1"), comments);
        }

        static class SkipFirstLineBufferedWriter extends BufferedWriter {
            private boolean firstLine = true;

            public SkipFirstLineBufferedWriter(Writer out) {
                super(out);
            }

            @Override
            public void newLine() throws IOException {
                if (firstLine) {
                    firstLine = false;
                } else {
                    write('\n');
                }
            }

            @Override
            public void write(String s, int off, int len) throws IOException {
                if (!firstLine) {
                    super.write(s, off, len);
                }
            }

            @Override
            public void write(char cbuf[], int off, int len) throws IOException {
                if (!firstLine) {
                    super.write(cbuf, off, len);
                }
            }
        }
    }
}
