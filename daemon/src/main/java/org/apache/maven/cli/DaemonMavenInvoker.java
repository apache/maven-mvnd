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

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.function.Consumer;

import org.apache.maven.api.annotations.Nullable;
import org.apache.maven.api.cli.InvokerException;
import org.apache.maven.api.cli.InvokerRequest;
import org.apache.maven.cling.invoker.ContainerCapsuleFactory;
import org.apache.maven.cling.invoker.LookupContext;
import org.apache.maven.cling.invoker.ProtoLookup;
import org.apache.maven.cling.invoker.mvn.MavenContext;
import org.apache.maven.cling.invoker.mvn.resident.ResidentMavenInvoker;
import org.apache.maven.cling.utils.CLIReportingUtils;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.jline.MessageUtils;
import org.apache.maven.logging.BuildEventListener;
import org.apache.maven.logging.LoggingOutputStream;
import org.jline.terminal.TerminalBuilder;
import org.mvndaemon.mvnd.common.Environment;

public class DaemonMavenInvoker extends ResidentMavenInvoker {
    public DaemonMavenInvoker(ProtoLookup protoLookup, @Nullable Consumer<LookupContext> contextConsumer) {
        super(protoLookup, contextConsumer);
    }

    @Override
    protected void doCreateTerminal(MavenContext context, TerminalBuilder builder) {
        builder.streams(
                context.invokerRequest.stdIn().orElseThrow(),
                context.invokerRequest.stdOut().orElseThrow());
        builder.systemOutput(TerminalBuilder.SystemOutput.ForcedSysOut);
        builder.provider(TerminalBuilder.PROP_PROVIDER_EXEC);
        if (context.coloredOutput != null) {
            builder.color(context.coloredOutput);
        }
        // we do want to pause input
        builder.paused(true);
    }

    @Override
    protected void doConfigureWithTerminalWithRawStreamsEnabled(MavenContext context) {
        System.setOut(printStream(context.invokerRequest.stdOut().orElseThrow()));
        System.setErr(printStream(context.invokerRequest.stdErr().orElseThrow()));
    }

    private PrintStream printStream(OutputStream outputStream) {
        if (outputStream instanceof LoggingOutputStream los) {
            return los.printStream();
        } else if (outputStream instanceof PrintStream ps) {
            return ps;
        } else {
            return new PrintStream(outputStream);
        }
    }

    @Override
    protected org.apache.maven.logging.BuildEventListener doDetermineBuildEventListener(MavenContext context) {
        return context.invokerRequest.lookup().lookup(BuildEventListener.class);
    }

    @Override
    protected void helpOrVersionAndMayExit(MavenContext context) throws Exception {
        InvokerRequest invokerRequest = context.invokerRequest;
        BuildEventListener buildEventListener =
                context.invokerRequest.parserRequest().lookup().lookup(BuildEventListener.class);
        if (context.options().help().isPresent()) {
            context.options().displayHelp(invokerRequest.parserRequest(), buildEventListener::log);
            throw new InvokerException.ExitException(0);
        }
        if (context.options().showVersionAndExit().isPresent()) {
            if (context.options().quiet().orElse(false)) {
                buildEventListener.log(CLIReportingUtils.showVersionMinimal());
            } else {
                buildEventListener.log(CLIReportingUtils.showVersion());
            }
            throw new InvokerException.ExitException(0);
        }
    }

    @Override
    protected void preCommands(MavenContext context) throws Exception {
        if (context.options().verbose().orElse(false)
                || context.options().showVersion().orElse(false)) {
            context.invokerRequest
                    .parserRequest()
                    .lookup()
                    .lookup(BuildEventListener.class)
                    .log(CLIReportingUtils.showVersion());
        }
    }

    @Override
    protected ContainerCapsuleFactory<MavenContext> createContainerCapsuleFactory() {
        return new DaemonPlexusContainerCapsuleFactory();
    }

    @Override
    protected int doExecute(MavenContext context, MavenExecutionRequest request) throws Exception {
        context.logger.info(MessageUtils.builder()
                .a("Processing build on daemon ")
                .strong(Environment.MVND_ID.asString())
                .toString());
        context.logger.debug("Daemon status dump:");
        context.logger.debug("CWD: " + context.invokerRequest.cwd());
        context.logger.debug("MAVEN_HOME: " + context.invokerRequest.installationDirectory());
        context.logger.debug("USER_HOME: " + context.invokerRequest.userHomeDirectory());
        context.logger.debug("topDirectory: " + context.invokerRequest.topDirectory());
        context.logger.debug("rootDirectory: " + context.invokerRequest.rootDirectory());

        try {
            return super.doExecute(context, request);
        } finally {
            LoggingOutputStream.forceFlush(System.out);
            LoggingOutputStream.forceFlush(System.err);
        }
    }
}
