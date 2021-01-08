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
package org.mvndaemon.mvnd.transfer;

import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;
import org.mvndaemon.mvnd.logging.smart.BuildEventListener;
import org.mvndaemon.mvnd.logging.smart.ProjectBuildLogAppender;

public class DaemonMavenTransferListener implements TransferListener {

    private final BuildEventListener dispatcher;
    private final TransferListener delegate;

    public DaemonMavenTransferListener(BuildEventListener dispatcher, TransferListener delegate) {
        this.dispatcher = dispatcher;
        this.delegate = delegate;
    }

    @Override
    public void transferInitiated(TransferEvent event) throws TransferCancelledException {
        dispatcher.transfer(ProjectBuildLogAppender.getProjectId(), event);
        delegate.transferInitiated(event);
    }

    @Override
    public void transferStarted(TransferEvent event) throws TransferCancelledException {
        dispatcher.transfer(ProjectBuildLogAppender.getProjectId(), event);
        delegate.transferStarted(event);
    }

    @Override
    public void transferProgressed(TransferEvent event) throws TransferCancelledException {
        dispatcher.transfer(ProjectBuildLogAppender.getProjectId(), event);
        delegate.transferProgressed(event);
    }

    @Override
    public void transferCorrupted(TransferEvent event) throws TransferCancelledException {
        dispatcher.transfer(ProjectBuildLogAppender.getProjectId(), event);
        delegate.transferCorrupted(event);
    }

    @Override
    public void transferSucceeded(TransferEvent event) {
        dispatcher.transfer(ProjectBuildLogAppender.getProjectId(), event);
        delegate.transferSucceeded(event);
    }

    @Override
    public void transferFailed(TransferEvent event) {
        dispatcher.transfer(ProjectBuildLogAppender.getProjectId(), event);
        delegate.transferFailed(event);
    }
}
