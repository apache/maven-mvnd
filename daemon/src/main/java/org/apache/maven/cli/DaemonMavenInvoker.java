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

import org.apache.maven.api.cli.Options;
import org.apache.maven.api.cli.mvn.MavenInvokerRequest;
import org.apache.maven.api.cli.mvn.MavenOptions;
import org.apache.maven.cling.invoker.ContainerCapsuleFactory;
import org.apache.maven.cling.invoker.ProtoLookup;
import org.apache.maven.cling.invoker.mvn.resident.DefaultResidentMavenInvoker;
import org.apache.maven.jline.MessageUtils;
import org.apache.maven.logging.BuildEventListener;
import org.apache.maven.logging.LoggingOutputStream;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.mvndaemon.mvnd.common.Environment;

public class DaemonMavenInvoker extends DefaultResidentMavenInvoker {
    public DaemonMavenInvoker(ProtoLookup protoLookup) {
        super(protoLookup);
    }

    @Override
    protected void configureLogging(LocalContext context) throws Exception {
        super.configureLogging(context);
    }

    @Override
    protected Terminal createTerminal(LocalContext context) {
        MessageUtils.systemInstall(
                builder -> {
                    builder.streams(
                            context.invokerRequest.in().get(),
                            context.invokerRequest.out().get());
                    builder.systemOutput(TerminalBuilder.SystemOutput.ForcedSysOut);
                    builder.provider(TerminalBuilder.PROP_PROVIDER_EXEC);
                    if (context.coloredOutput != null) {
                        builder.color(context.coloredOutput);
                    }
                },
                terminal -> doConfigureWithTerminal(context, terminal));
        return MessageUtils.getTerminal();
    }

    @Override
    protected org.apache.maven.logging.BuildEventListener doDetermineBuildEventListener(LocalContext context) {
        return protoLookup.lookup(BuildEventListener.class);
    }

    @Override
    protected void helpOrVersionAndMayExit(LocalContext context) throws Exception {
        MavenInvokerRequest<MavenOptions> invokerRequest = context.invokerRequest;
        BuildEventListener buildEventListener =
                context.invokerRequest.parserRequest().lookup().lookup(BuildEventListener.class);
        if (invokerRequest.options().help().isPresent()) {
            context.invokerRequest.options().displayHelp(invokerRequest.parserRequest(), buildEventListener::log);
            throw new ExitException(0);
        }
        if (invokerRequest.options().showVersionAndExit().isPresent()) {
            if (invokerRequest.options().quiet().orElse(false)) {
                buildEventListener.log(CLIReportingUtils.showVersionMinimal());
            } else {
                buildEventListener.log(CLIReportingUtils.showVersion());
            }
            throw new ExitException(0);
        }
    }

    @Override
    protected void preCommands(LocalContext context) throws Exception {
        Options mavenOptions = context.invokerRequest.options();
        if (mavenOptions.verbose().orElse(false) || mavenOptions.showVersion().orElse(false)) {
            context.invokerRequest
                    .parserRequest()
                    .lookup()
                    .lookup(BuildEventListener.class)
                    .log(CLIReportingUtils.showVersion());
        }
    }

    @Override
    protected ContainerCapsuleFactory<MavenOptions, MavenInvokerRequest<MavenOptions>, LocalContext>
            createContainerCapsuleFactory() {
        return new DaemonPlexusContainerCapsuleFactory();
    }

    @Override
    protected int doExecute(LocalContext context) throws Exception {
        context.logger.info(MessageUtils.builder()
                .a("Processing build on daemon ")
                .strong(Environment.MVND_ID.asString())
                .toString());
        context.logger.info("Daemon status dump:");
        context.logger.info("CWD: " + context.invokerRequest.cwd());
        context.logger.info("MAVEN_HOME: " + context.invokerRequest.installationDirectory());
        context.logger.info("USER_HOME: " + context.invokerRequest.userHomeDirectory());
        context.logger.info("topDirectory: " + context.invokerRequest.topDirectory());
        context.logger.info("rootDirectory: " + context.invokerRequest.rootDirectory());

        try {
            return super.doExecute(context);
        } finally {
            LoggingOutputStream.forceFlush(System.out);
            LoggingOutputStream.forceFlush(System.err);
        }
    }
}
