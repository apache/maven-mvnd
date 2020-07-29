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

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File origin:
 * https://github.com/gradle/gradle/blob/v5.6.2/subprojects/launcher/src/main/java/org/gradle/launcher/daemon/client/DaemonClientConnection.java
 */
public class DaemonClientConnection {

    private final static Logger LOG = LoggerFactory.getLogger(DaemonClientConnection.class);

    private final DaemonConnection<Message> connection;
    private final DaemonInfo daemon;
    private final StaleAddressDetector staleAddressDetector;
    private boolean hasReceived;
    private final Lock dispatchLock = new ReentrantLock();

    public DaemonClientConnection(DaemonConnection<Message> connection, DaemonInfo daemon,
            StaleAddressDetector staleAddressDetector) {
        this.connection = connection;
        this.daemon = daemon;
        this.staleAddressDetector = staleAddressDetector;
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

    public Message receive() throws DaemonException.ConnectException {
        try {
            return connection.receive();
        } catch (DaemonException.MessageIOException e) {
            LOG.debug("Problem receiving message to the daemon. Performing 'on failure' operation...");
            if (!hasReceived && staleAddressDetector.maybeStaleAddress(e)) {
                throw new DaemonException.StaleAddressException("Could not receive a message from the daemon.", e);
            }
            throw new DaemonException.ConnectException("Could not receive a message from the daemon.", e);
        } finally {
            hasReceived = true;
        }
    }

    public void stop() {
        LOG.debug("thread {}: connection stop", Thread.currentThread().getId());
        connection.close();
    }

    interface StaleAddressDetector {
        /**
         * @return true if the failure should be considered due to a stale address.
         */
        boolean maybeStaleAddress(Exception failure);
    }

}
