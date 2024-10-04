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
package org.apache.maven.cli;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.api.cli.ParserRequest;
import org.apache.maven.cling.invoker.ProtoLogger;
import org.apache.maven.cling.invoker.ProtoLookup;
import org.apache.maven.jline.JLineMessageBuilderFactory;
import org.apache.maven.jline.MessageUtils;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.ExternalTerminal;
import org.mvndaemon.mvnd.cli.EnvHelper;
import org.mvndaemon.mvnd.logging.smart.BuildEventListener;

public class DaemonMavenCling implements DaemonCli {
    private final DaemonMavenParser parser;
    private final DaemonMavenInvoker invoker;

    public DaemonMavenCling() {
        this.parser = new DaemonMavenParser();
        this.invoker = new DaemonMavenInvoker(ProtoLookup.builder()
                .addMapping(
                        ClassWorld.class, ((ClassRealm) Thread.currentThread().getContextClassLoader()).getWorld())
                .build());
    }

    @Override
    public void close() throws Exception {
        invoker.close();
    }

    @Override
    public int main(
            List<String> args,
            String workingDir,
            String projectDir,
            Map<String, String> env,
            BuildEventListener buildEventListener)
            throws Exception {
        Terminal terminal = new ExternalTerminal(
                "Maven",
                "ansi",
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(),
                StandardCharsets.UTF_8);
        MessageUtils.systemInstall(terminal);
        EnvHelper.environment(workingDir, env);

        HashMap<String, String> environment = new HashMap<>(env);
        environment.put("maven.multiModuleProjectDirectory", projectDir);
        return invoker.invoke(parser.parse(
                ParserRequest.builder("mvnd", "Maven Daemon", args, new ProtoLogger(), new JLineMessageBuilderFactory())
                        .cwd(Paths.get(workingDir))
                        .lookup(ProtoLookup.builder()
                                .addMapping(Environment.class, () -> environment)
                                .addMapping(BuildEventListener.class, buildEventListener)
                                .build())
                        .build()));
    }

    /**
     * Key for environment.
     */
    interface Environment {
        Map<String, String> getEnvironment();
    }
}
