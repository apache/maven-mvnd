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
package org.jboss.fuse.mvnd.daemon;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.maven.cli.DaemonMavenCli;
import org.apache.maven.execution.MavenSession;
import org.jboss.fuse.mvnd.builder.SmartBuilder;
import org.jboss.fuse.mvnd.common.DaemonConnection;
import org.jboss.fuse.mvnd.common.DaemonException;
import org.jboss.fuse.mvnd.common.DaemonExpirationStatus;
import org.jboss.fuse.mvnd.common.DaemonInfo;
import org.jboss.fuse.mvnd.common.DaemonRegistry;
import org.jboss.fuse.mvnd.common.DaemonState;
import org.jboss.fuse.mvnd.common.DaemonStopEvent;
import org.jboss.fuse.mvnd.common.Environment;
import org.jboss.fuse.mvnd.common.Message;
import org.jboss.fuse.mvnd.common.Message.BuildException;
import org.jboss.fuse.mvnd.common.Message.BuildRequest;
import org.jboss.fuse.mvnd.common.Message.BuildStarted;
import org.jboss.fuse.mvnd.daemon.DaemonExpiration.DaemonExpirationResult;
import org.jboss.fuse.mvnd.daemon.DaemonExpiration.DaemonExpirationStrategy;
import org.jboss.fuse.mvnd.logging.smart.AbstractLoggingSpy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jboss.fuse.mvnd.common.DaemonState.Broken;
import static org.jboss.fuse.mvnd.common.DaemonState.Busy;
import static org.jboss.fuse.mvnd.common.DaemonState.Canceled;
import static org.jboss.fuse.mvnd.common.DaemonState.StopRequested;
import static org.jboss.fuse.mvnd.common.DaemonState.Stopped;

public class Server implements AutoCloseable, Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);
    public static final int CANCEL_TIMEOUT = 10 * 1000;

    private final String uid;
    private final ServerSocketChannel socket;
    private final DaemonMavenCli cli;
    private volatile DaemonInfo info;
    private final DaemonRegistry registry;

    private final ScheduledExecutorService executor;
    private final DaemonExpirationStrategy strategy;
    private final Lock expirationLock = new ReentrantLock();
    private final Lock stateLock = new ReentrantLock();
    private final Condition condition = stateLock.newCondition();
    private final DaemonMemoryStatus memoryStatus;

    public Server() throws IOException {
        this.uid = Environment.DAEMON_UID.asString();
        try {
            cli = new DaemonMavenCli();
            registry = new DaemonRegistry(Environment.DAEMON_REGISTRY.asPath());
            socket = ServerSocketChannel.open().bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            executor = Executors.newScheduledThreadPool(1);
            strategy = DaemonExpiration.master();
            memoryStatus = new DaemonMemoryStatus(executor);

            List<String> opts = new ArrayList<>();
            Arrays.stream(Environment.values())
                    .filter(Environment::isDiscriminating)
                    .map(v -> v.getProperty() + "=" + v.asString())
                    .forEach(opts::add);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(opts.stream().collect(Collectors.joining(
                        "\n     ", "Initializing daemon with properties:\n     ", "\n")));
            }
            long cur = System.currentTimeMillis();
            info = new DaemonInfo(uid,
                    Environment.JAVA_HOME.asString(),
                    Environment.MVND_HOME.asString(),
                    DaemonRegistry.getProcessId(),
                    socket.socket().getLocalPort(),
                    Locale.getDefault().toLanguageTag(),
                    opts,
                    Busy, cur, cur);
            registry.store(info);
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize " + Server.class.getName(), e);
        }
    }

    public DaemonMemoryStatus getMemoryStatus() {
        return memoryStatus;
    }

    public void close() {
        try {
            try {
                updateState(Stopped);
            } finally {
                try {
                    executor.shutdown();
                } finally {
                    try {
                        registry.close();
                    } finally {
                        try {
                            socket.close();
                        } finally {
                            clearCache("sun.net.www.protocol.jar.JarFileFactory", "urlCache");
                            clearCache("sun.net.www.protocol.jar.JarFileFactory", "fileCache");
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Error closing daemon", t);
        }
    }

    public void clearCache(String clazzName, String fieldName) {
        try {
            Class<?> clazz = ClassLoader.getSystemClassLoader().loadClass(clazzName);
            Field f = clazz.getDeclaredField(fieldName);
            f.setAccessible(true);
            Map cache = (Map) f.get(null);
            cache.clear();
        } catch (Throwable t) {
            // ignore
        }
    }

    public void run() {
        try {
            int expirationCheckDelayMs = Environment.DAEMON_EXPIRATION_CHECK_DELAY_MS.asInt();
            executor.scheduleAtFixedRate(this::expirationCheck,
                    expirationCheckDelayMs, expirationCheckDelayMs, TimeUnit.MILLISECONDS);
            LOGGER.info("Daemon started");
            new DaemonThread(this::accept).start();
            awaitStop();
        } catch (Throwable t) {
            LOGGER.error("Error running daemon loop", t);
        } finally {
            registry.remove(uid);
        }
    }

    static class DaemonThread extends Thread {
        public DaemonThread(Runnable target) {
            super(target);
            setDaemon(true);
        }
    }

    private void accept() {
        try {
            while (true) {
                try (SocketChannel socket = this.socket.accept()) {
                    client(socket);
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Error running daemon loop", t);
        }
    }

    private void client(SocketChannel socket) {
        LOGGER.info("Client connected");
        try (DaemonConnection connection = new DaemonConnection(socket)) {
            LOGGER.info("Waiting for request");
            SynchronousQueue<Message> request = new SynchronousQueue<>();
            new DaemonThread(() -> {
                Message message = connection.receive();
                request.offer(message);
            }).start();
            Message message = request.poll(1, TimeUnit.MINUTES);
            if (message == null) {
                LOGGER.info("Could not receive request after one minute, dropping connection");
                updateState(DaemonState.Idle);
                return;
            }
            LOGGER.info("Request received: " + message);
            if (message instanceof BuildRequest) {
                handle(connection, (BuildRequest) message);
            }
        } catch (Throwable t) {
            LOGGER.error("Error reading request", t);
        }
    }

    private void expirationCheck() {
        if (expirationLock.tryLock()) {
            try {
                LOGGER.debug("Expiration check running");
                final DaemonExpirationResult result = strategy.checkExpiration(this);
                switch (result.getStatus()) {
                case DO_NOT_EXPIRE:
                    break;
                case QUIET_EXPIRE:
                    requestStop(result.getReason());
                    break;
                case GRACEFUL_EXPIRE:
                    onExpire(result.getReason(), result.getStatus());
                    requestStop(result.getReason());
                    break;
                case IMMEDIATE_EXPIRE:
                    onExpire(result.getReason(), result.getStatus());
                    requestForcefulStop(result.getReason());
                    break;
                }
            } catch (Throwable t) {
                LOGGER.error("Problem in daemon expiration check", t);
                if (t instanceof Error) {
                    // never swallow java.lang.Error
                    throw (Error) t;
                }
            } finally {
                expirationLock.unlock();
            }
        } else {
            LOGGER.warn("Previous DaemonExpirationPeriodicCheck was still running when the next run was scheduled.");
        }
    }

    private void onExpire(String reason, DaemonExpirationStatus status) {
        LOGGER.debug("Storing daemon stop event: {}", reason);
        registry.storeStopEvent(new DaemonStopEvent(uid, System.currentTimeMillis(), status, reason));
    }

    boolean awaitStop() {
        stateLock.lock();
        try {
            while (true) {
                try {
                    switch (getState()) {
                    case Idle:
                    case Busy:
                        LOGGER.debug("daemon is running. Sleeping until state changes.");
                        condition.await();
                        break;
                    case Canceled:
                        cancelNow();
                        break;
                    case Broken:
                        throw new IllegalStateException("This daemon is in a broken state.");
                    case StopRequested:
                        LOGGER.debug("daemon stop has been requested. Sleeping until state changes.");
                        condition.await();
                        break;
                    case Stopped:
                        LOGGER.debug("daemon has stopped.");
                        return true;
                    }
                } catch (InterruptedException e) {
                    throw new DaemonException.InterruptedException(e);
                }
            }
        } finally {
            stateLock.unlock();
        }
    }

    private void requestStop(String reason) {
        DaemonState state = getState();
        if (!(state == StopRequested || state == Stopped)) {
            LOGGER.info("Daemon will be stopped at the end of the build " + reason);
            stateLock.lock();
            try {
                if (state == Busy) {
                    LOGGER.debug("Stop as soon as idle requested. The daemon is busy.");
                    beginStopping();
                } else {
                    stopNow(reason);
                }
            } finally {
                stateLock.unlock();
            }
        }
    }

    private void requestForcefulStop(String reason) {
        LOGGER.info("Daemon is stopping immediately " + reason);
        stopNow(reason);
    }

    private void beginStopping() {
        DaemonState state = getState();
        switch (state) {
        case Idle:
        case Busy:
        case Canceled:
        case Broken:
            updateState(StopRequested);
            break;
        case StopRequested:
        case Stopped:
            break;
        default:
            throw new IllegalStateException("Daemon is in unexpected state: " + state);
        }
    }

    private void stopNow(String reason) {
        stateLock.lock();
        try {
            DaemonState state = getState();
            switch (state) {
            case Idle:
            case Busy:
            case Canceled:
            case Broken:
            case StopRequested:
                LOGGER.debug("Marking daemon stopped due to {}. The daemon is running a build: {}", reason, state == Busy);
                updateState(Stopped);
                break;
            case Stopped:
                break;
            default:
                throw new IllegalStateException("Daemon is in unexpected state: " + state);
            }
        } finally {
            stateLock.unlock();
        }
    }

    private void cancelNow() {
        long time = System.currentTimeMillis() + CANCEL_TIMEOUT;

        LOGGER.debug("Cancel requested: will wait for daemon to become idle.");
        final SmartBuilder builder = SmartBuilder.cancel();
        stateLock.lock();
        try {
            long rem;
            while ((rem = time - System.currentTimeMillis()) > 0) {
                try {
                    switch (getState()) {
                    case Idle:
                        LOGGER.debug("Cancel: daemon is idle now.");
                        return;
                    case Busy:
                    case Canceled:
                    case StopRequested:
                        LOGGER.debug("Cancel: daemon is busy, sleeping until state changes.");
                        condition.await(rem, TimeUnit.MILLISECONDS);
                        break;
                    case Broken:
                        throw new IllegalStateException("This daemon is in a broken state.");
                    case Stopped:
                        LOGGER.debug("Cancel: daemon has stopped.");
                        return;
                    }
                } catch (InterruptedException e) {
                    throw new DaemonException.InterruptedException(e);
                }
            }
            LOGGER.debug("Cancel: daemon is still busy after grace period. Will force stop.");
            stopNow("cancel requested but timed out");
        } finally {
            stateLock.unlock();
            if (builder != null) {
                builder.doneCancel();
            }
        }
    }

    private void handle(DaemonConnection connection, BuildRequest buildRequest) {
        updateState(Busy);
        try {
            int keepAlive = Environment.DAEMON_KEEP_ALIVE_MS.asInt();

            LOGGER.info("Executing request");

            BlockingQueue<Message> sendQueue = new PriorityBlockingQueue<>(64,
                    Comparator.comparingInt(this::getClassOrder).thenComparingLong(Message::timestamp));
            BlockingQueue<Message> recvQueue = new LinkedBlockingDeque<>();

            DaemonLoggingSpy loggingSpy = new DaemonLoggingSpy(sendQueue);
            AbstractLoggingSpy.instance(loggingSpy);
            Thread sender = new Thread(() -> {
                try {
                    boolean flushed = true;
                    while (true) {
                        Message m;
                        if (flushed) {
                            m = sendQueue.poll(keepAlive, TimeUnit.MILLISECONDS);
                            if (m == null) {
                                m = Message.KEEP_ALIVE_SINGLETON;
                            }
                            flushed = false;
                        } else {
                            m = sendQueue.poll();
                            if (m == null) {
                                connection.flush();
                                flushed = true;
                                continue;
                            }
                        }
                        if (m == Message.STOP_SINGLETON) {
                            connection.flush();
                            LOGGER.info("No more message to dispatch");
                            return;
                        }
                        LOGGER.info("Dispatch message: " + m);
                        connection.dispatch(m);
                    }
                } catch (Throwable t) {
                    LOGGER.error("Error dispatching events", t);
                }
            });
            sender.start();
            Thread receiver = new Thread(() -> {
                try {
                    while (true) {
                        Message message = connection.receive();
                        if (message == null) {
                            break;
                        }
                        LOGGER.info("Received message: {}", message);
                        if (message == Message.CANCEL_BUILD_SINGLETON) {
                            updateState(DaemonState.Canceled);
                            return;
                        } else {
                            synchronized (recvQueue) {
                                recvQueue.put(message);
                                recvQueue.notifyAll();
                            }
                        }
                    }
                } catch (DaemonException.RecoverableMessageIOException t) {
                    updateState(Canceled);
                } catch (Throwable t) {
                    updateState(Broken);
                    LOGGER.error("Error receiving events", t);
                }
            });
            receiver.start();
            try {
                Connection.setCurrent(new Connection() {
                    @Override
                    public void dispatch(Message message) {
                        try {
                            sendQueue.put(message);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public <T extends Message> T request(Message request, Class<T> responseType, Predicate<T> matcher) {
                        try {
                            synchronized (recvQueue) {
                                sendQueue.put(request);
                                LOGGER.info("Waiting for response");
                                while (true) {
                                    T t = recvQueue.stream()
                                            .filter(responseType::isInstance)
                                            .map(responseType::cast)
                                            .filter(matcher)
                                            .findFirst()
                                            .orElse(null);
                                    if (t != null) {
                                        recvQueue.remove(t);
                                        LOGGER.info("Received response: {}", t);
                                        return t;
                                    }
                                    recvQueue.wait();
                                }
                            }
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
                cli.main(
                        buildRequest.getArgs(),
                        buildRequest.getWorkingDir(),
                        buildRequest.getProjectDir(),
                        buildRequest.getEnv());
                LOGGER.info("Build finished, finishing message dispatch");
                loggingSpy.finish();
            } catch (Throwable t) {
                LOGGER.error("Error while building project", t);
                loggingSpy.fail(t);
            } finally {
                sender.join();
            }
        } catch (Throwable t) {
            LOGGER.error("Error while building project", t);
        } finally {
            LOGGER.info("Daemon back to idle");
            updateState(DaemonState.Idle);
            System.gc();
        }
    }

    int getClassOrder(Message m) {
        switch (m.getType()) {
        case Message.BUILD_REQUEST:
            return 0;
        case Message.BUILD_STARTED:
            return 1;
        case Message.PROMPT:
        case Message.PROMPT_RESPONSE:
        case Message.DISPLAY:
            return 2;
        case Message.PROJECT_STARTED:
            return 3;
        case Message.MOJO_STARTED:
            return 4;
        case Message.PROJECT_LOG_MESSAGE:
            return 50;
        case Message.BUILD_LOG_MESSAGE:
            return 51;
        case Message.PROJECT_STOPPED:
            return 95;
        case Message.BUILD_STOPPED:
            return 96;
        case Message.BUILD_EXCEPTION:
            return 97;
        case Message.STOP:
            return 99;
        case Message.KEEP_ALIVE:
            return 100;
        default:
            throw new IllegalStateException();
        }
    }

    private void updateState(DaemonState state) {
        if (getState() != state) {
            LOGGER.info("Updating state to: " + state);
            stateLock.lock();
            try {
                registry.store(info = info.withState(state));
                condition.signalAll();
            } finally {
                stateLock.unlock();
            }
        }
    }

    public DaemonRegistry getRegistry() {
        return registry;
    }

    public DaemonInfo getInfo() {
        return info;
    }

    public String getUid() {
        return info.getUid();
    }

    public DaemonState getState() {
        return info.getState();
    }

    public long getLastIdle() {
        return info.getLastIdle();
    }

    public long getLastBusy() {
        return info.getLastBusy();
    }

    @Override
    public String toString() {
        return info.toString();
    }

    private static class DaemonLoggingSpy extends AbstractLoggingSpy {
        private final BlockingQueue<Message> queue;

        public DaemonLoggingSpy(BlockingQueue<Message> queue) {
            this.queue = queue;
        }

        public void finish() throws Exception {
            queue.add(Message.BUILD_STOPPED_SINGLETON);
            queue.add(Message.STOP_SINGLETON);
        }

        public void fail(Throwable t) throws Exception {
            queue.add(new BuildException(t));
            queue.add(Message.STOP_SINGLETON);
        }

        @Override
        protected void onStartSession(MavenSession session) {
            queue.add(new BuildStarted(session.getTopLevelProject().getName(), session.getAllProjects().size(),
                    session.getRequest().getDegreeOfConcurrency()));
        }

        @Override
        protected void onStartProject(String projectId, String display) {
            queue.add(Message.projectStarted(projectId, display));
        }

        @Override
        protected void onStopProject(String projectId, String display) {
            queue.add(Message.projectStopped(projectId, display));
        }

        @Override
        protected void onStartMojo(String projectId, String display) {
            queue.add(Message.mojoStarted(projectId, display));
        }

        @Override
        protected void onProjectLog(String projectId, String message) {
            queue.add(projectId == null ? Message.log(message) : Message.log(projectId, message));
        }

    }
}
