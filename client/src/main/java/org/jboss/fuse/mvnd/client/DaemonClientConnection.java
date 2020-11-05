/*
 * Copyright 2019 the original author or authors.
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
package org.jboss.fuse.mvnd.client;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.jboss.fuse.mvnd.common.DaemonConnection;
import org.jboss.fuse.mvnd.common.DaemonException;
import org.jboss.fuse.mvnd.common.DaemonException.ConnectException;
import org.jboss.fuse.mvnd.common.DaemonException.StaleAddressException;
import org.jboss.fuse.mvnd.common.DaemonInfo;
import org.jboss.fuse.mvnd.common.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File origin:
 * https://github.com/gradle/gradle/blob/v5.6.2/subprojects/launcher/src/main/java/org/gradle/launcher/daemon/client/DaemonClientConnection.java
 */
public class DaemonClientConnection implements Closeable {

    private final static Logger LOG = LoggerFactory.getLogger(DaemonClientConnection.class);

    private final DaemonConnection connection;
    private final DaemonInfo daemon;
    private final StaleAddressDetector staleAddressDetector;
    private final boolean newDaemon;
    private boolean hasReceived;
    private final Lock dispatchLock = new ReentrantLock();
    private final int maxKeepAliveMs;
    private final BlockingQueue<Message> queue = new ArrayBlockingQueue<>(16);
    private final Thread receiver;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicReference<Exception> exception = new AtomicReference<>();
    private final DaemonParameters parameters;

    public DaemonClientConnection(DaemonConnection connection, DaemonInfo daemon,
            StaleAddressDetector staleAddressDetector, boolean newDaemon, int maxKeepAliveMs, DaemonParameters parameters) {
        this.connection = connection;
        this.daemon = daemon;
        this.staleAddressDetector = staleAddressDetector;
        this.newDaemon = newDaemon;
        this.maxKeepAliveMs = maxKeepAliveMs;
        this.receiver = new Thread(this::doReceive);
        this.receiver.start();
        this.parameters = parameters;
    }

    public DaemonInfo getDaemon() {
        return daemon;
    }

    public void dispatch(Message message) throws DaemonException.ConnectException {
        LOG.debug("thread {}: dispatching {}", Thread.currentThread().getId(), message.getClass());
        try {
            dispatchLock.lock();
            try {
                connection.dispatch(message);
                connection.flush();
            } finally {
                dispatchLock.unlock();
            }
        } catch (DaemonException.MessageIOException e) {
            LOG.debug("Problem dispatching message to the daemon. Performing 'on failure' operation...");
            if (!hasReceived && staleAddressDetector.maybeStaleAddress(e)) {
                throw new DaemonException.StaleAddressException("Could not dispatch a message to the daemon.", e);
            }
            throw new DaemonException.ConnectException("Could not dispatch a message to the daemon.", e);
        }
    }

    public Message receive() throws ConnectException, StaleAddressException {
        while (true) {
            try {
                Message m = queue.poll(maxKeepAliveMs, TimeUnit.MILLISECONDS);
                Exception e = exception.get();
                if (e != null) {
                    throw e;
                } else if (m != null) {
                    return m;
                } else {
                    throw new IOException("No message received within " + maxKeepAliveMs
                            + "ms, daemon may have crashed. You may want to check its status using mvnd --status");
                }
            } catch (Exception e) {
                DaemonDiagnostics diag = new DaemonDiagnostics(daemon.getUid(), parameters);
                LOG.debug("Problem receiving message to the daemon. Performing 'on failure' operation...");
                if (!hasReceived && newDaemon) {
                    throw new ConnectException("Could not receive a message from the daemon.\n" + diag.describe(), e);
                } else if (staleAddressDetector.maybeStaleAddress(e)) {
                    throw new StaleAddressException("Could not receive a message from the daemon.\n" + diag.describe(), e);
                }
            } finally {
                hasReceived = true;
            }
        }
    }

    protected void doReceive() {
        try {
            while (running.get()) {
                Message m = connection.receive();
                queue.put(m);
            }
        } catch (Exception e) {
            if (running.get()) {
                exception.set(e);
            }
        }
    }

    public void close() {
        LOG.debug("thread {}: connection stop", Thread.currentThread().getId());
        running.set(false);
        receiver.interrupt();
        connection.close();
    }

    public interface StaleAddressDetector {
        /**
         * @return true if the failure should be considered due to a stale address.
         */
        boolean maybeStaleAddress(Exception failure);
    }

}
