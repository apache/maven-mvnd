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

import org.apache.maven.api.cli.InvokerException;
import org.apache.maven.api.cli.Options;
import org.apache.maven.api.cli.mvn.MavenInvokerRequest;
import org.apache.maven.api.cli.mvn.MavenOptions;
import org.apache.maven.cli.event.ExecutionEventLogger;
import org.apache.maven.cling.invoker.ContainerCapsuleFactory;
import org.apache.maven.cling.invoker.ProtoLookup;
import org.apache.maven.cling.invoker.mvn.resident.DefaultResidentMavenInvoker;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.jline.MessageUtils;
import org.eclipse.aether.transfer.TransferListener;
import org.mvndaemon.mvnd.common.Environment;
import org.mvndaemon.mvnd.logging.slf4j.MvndSimpleLogger;
import org.mvndaemon.mvnd.logging.smart.BuildEventListener;
import org.mvndaemon.mvnd.logging.smart.LoggingExecutionListener;
import org.mvndaemon.mvnd.logging.smart.LoggingOutputStream;
import org.mvndaemon.mvnd.transfer.DaemonMavenTransferListener;
import org.slf4j.spi.LocationAwareLogger;

public class DaemonMavenInvoker extends DefaultResidentMavenInvoker {
    public DaemonMavenInvoker(ProtoLookup protoLookup) {
        super(protoLookup);
    }

    @Override
    public int invoke(MavenInvokerRequest<MavenOptions> invokerRequest) throws InvokerException {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            return super.invoke(invokerRequest);
        } catch (InvokerException e) {
            invokerRequest
                    .parserRequest()
                    .lookup()
                    .lookup(BuildEventListener.class)
                    .log(e.getMessage());
            throw e;
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
    }

    @Override
    protected void configureLogging(LocalContext context) throws Exception {
        super.configureLogging(context);

        DaemonMavenOptions options = (DaemonMavenOptions) context.invokerRequest.options();
        if (options.logFile().isEmpty() && !options.rawStreams().orElse(false)) {
            MvndSimpleLogger stdout = (MvndSimpleLogger) context.loggerFactory.getLogger("stdout");
            MvndSimpleLogger stderr = (MvndSimpleLogger) context.loggerFactory.getLogger("stderr");
            stdout.setLogLevel(LocationAwareLogger.INFO_INT);
            stderr.setLogLevel(LocationAwareLogger.INFO_INT);
            System.setOut(new LoggingOutputStream(s -> stdout.info("[stdout] " + s)).printStream());
            System.setErr(new LoggingOutputStream(s -> stderr.warn("[stderr] " + s)).printStream());
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
    protected ExecutionListener determineExecutionListener(LocalContext context) {
        if (context.lookup != null) {
            LoggingExecutionListener listener = context.lookup.lookup(LoggingExecutionListener.class);
            ExecutionEventLogger executionEventLogger =
                    new ExecutionEventLogger(context.invokerRequest.messageBuilderFactory());
            listener.init(
                    context.eventSpyDispatcher.chainListener(executionEventLogger),
                    context.invokerRequest.parserRequest().lookup().lookup(BuildEventListener.class));
            return listener;
        } else {
            // this branch happens in "early" step of container capsule to load extensions
            return super.determineExecutionListener(context);
        }
    }

    @Override
    protected TransferListener determineTransferListener(LocalContext context, boolean noTransferProgress) {
        return new DaemonMavenTransferListener(
                context.invokerRequest.parserRequest().lookup().lookup(BuildEventListener.class),
                super.determineTransferListener(context, noTransferProgress));
    }

    @Override
    protected int doExecute(LocalContext context) throws Exception {
        context.logger.info(MessageUtils.builder()
                .a("Processing build on daemon ")
                .strong(Environment.MVND_ID.asString())
                .toString());

        try {
            return super.doExecute(context);
        } finally {
            LoggingOutputStream.forceFlush(System.out);
            LoggingOutputStream.forceFlush(System.err);
        }
    }
}
