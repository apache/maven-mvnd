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

import org.apache.maven.cli.event.ExecutionEventLogger;
import org.apache.maven.cling.invoker.ProtoLookup;
import org.apache.maven.cling.invoker.mvn.resident.DefaultResidentMavenInvoker;
import org.apache.maven.execution.ExecutionListener;
import org.eclipse.aether.transfer.TransferListener;
import org.mvndaemon.mvnd.logging.smart.BuildEventListener;
import org.mvndaemon.mvnd.logging.smart.LoggingExecutionListener;
import org.mvndaemon.mvnd.transfer.DaemonMavenTransferListener;

public class DaemonMavenInvoker extends DefaultResidentMavenInvoker {
    public DaemonMavenInvoker(ProtoLookup protoLookup) {
        super(protoLookup);
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
}
