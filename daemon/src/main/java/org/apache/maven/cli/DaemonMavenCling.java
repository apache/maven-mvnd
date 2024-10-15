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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.maven.api.cli.ParserRequest;
import org.apache.maven.cling.invoker.ProtoLogger;
import org.apache.maven.cling.invoker.ProtoLookup;
import org.apache.maven.logging.BuildEventListener;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.mvndaemon.mvnd.cli.EnvHelper;

public class DaemonMavenCling implements DaemonCli {

    @Override
    public int main(
            List<String> args,
            String workingDir,
            String projectDir,
            Map<String, String> env,
            BuildEventListener buildEventListener,
            InputStream in,
            OutputStream out,
            OutputStream err)
            throws Exception {
        EnvHelper.environment(workingDir, env);
        System.setProperty("maven.multiModuleProjectDirectory", projectDir);

        try (DaemonMavenInvoker invoker = new DaemonMavenInvoker(ProtoLookup.builder()
                .addMapping(
                        ClassWorld.class, ((ClassRealm) Thread.currentThread().getContextClassLoader()).getWorld())
                .addMapping(BuildEventListener.class, buildEventListener)
                .build())) {
            DaemonMavenParser parser = new DaemonMavenParser();
            return invoker.invoke(parser.parse(ParserRequest.builder(
                            "mvnd", "Maven Daemon", args, new ProtoLogger(), new DaemonMessageBuilderFactory())
                    .cwd(Paths.get(workingDir))
                    .in(in)
                    .out(out)
                    .err(err)
                    .lookup(ProtoLookup.builder()
                            .addMapping(Environment.class, () -> env)
                            .addMapping(BuildEventListener.class, buildEventListener)
                            .build())
                    .build()));
        }
    }

    /**
     * Key for environment.
     */
    interface Environment extends Supplier<Map<String, String>> {}
}
