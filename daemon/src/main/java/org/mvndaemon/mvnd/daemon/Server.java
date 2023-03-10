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
package org.mvndaemon.mvnd.daemon;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.takari.maven.builder.smart.SmartBuilder;
import org.apache.maven.cli.DaemonCli;
import org.mvndaemon.mvnd.common.DaemonConnection;
import org.mvndaemon.mvnd.common.DaemonException;
import org.mvndaemon.mvnd.common.DaemonExpirationStatus;
import org.mvndaemon.mvnd.common.DaemonInfo;
import org.mvndaemon.mvnd.common.DaemonRegistry;
import org.mvndaemon.mvnd.common.DaemonState;
import org.mvndaemon.mvnd.common.DaemonStopEvent;
import org.mvndaemon.mvnd.common.Environment;
import org.mvndaemon.mvnd.common.Message;
import org.mvndaemon.mvnd.common.Message.BuildRequest;
import org.mvndaemon.mvnd.common.ProcessHelper;
import org.mvndaemon.mvnd.common.SignalHelper;
import org.mvndaemon.mvnd.common.SocketFamily;
import org.mvndaemon.mvnd.daemon.DaemonExpiration.DaemonExpirationResult;
import org.mvndaemon.mvnd.daemon.DaemonExpiration.DaemonExpirationStrategy;
import org.mvndaemon.mvnd.logging.smart.BuildEventListener;
import org.mvndaemon.mvnd.logging.smart.LoggingOutputStream;
import org.mvndaemon.mvnd.logging.smart.ProjectBuildLogAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mvndaemon.mvnd.common.DaemonState.Broken;
import static org.mvndaemon.mvnd.common.DaemonState.Busy;
import static org.mvndaemon.mvnd.common.DaemonState.Canceled;
import static org.mvndaemon.mvnd.common.DaemonState.StopRequested;
import static org.mvndaemon.mvnd.common.DaemonState.Stopped;

public class Server implements AutoCloseable, Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);
    public static final int CANCEL_TIMEOUT = 10 * 1000;

    private final String daemonId;
    private final boolean noDaemon;
    private final ServerSocketChannel socket;
    private final DaemonCli cli;
    private volatile DaemonInfo info;
    private final DaemonRegistry registry;

    private final ScheduledExecutorService executor;
    private final DaemonExpirationStrategy strategy;
    private final Lock expirationLock = new ReentrantLock();
    private final Lock stateLock = new ReentrantLock();
    private final Condition condition = stateLock.newCondition();
    private final DaemonMemoryStatus memoryStatus;
    private final long keepAliveMs;

    public static void main(String[] args) {
        try (Server server = new Server()) {
            server.run();
        }
    }

    public Server() {
        // When spawning a new process, the child process is create within
        // the same process group.  This means that a few signals are sent
        // to the whole group.  This is the case for SIGINT (Ctrl-C) and
        // SIGTSTP (Ctrl-Z) which are both sent to all the processed in the
        // group when initiated from the controlling terminal.
        // This is only a problem when the client creates the daemon, but
        // without ignoring those signals, a client being interrupted will
        // also interrupt and kill the daemon.
        try {
            SignalHelper.ignoreStopSignals();
        } catch (Throwable t) {
            LOGGER.warn("Unable to ignore INT and TSTP signals", t);
        }
        this.daemonId = Environment.MVND_ID.asString();
        this.noDaemon = Environment.MVND_NO_DAEMON.asBoolean();
        this.keepAliveMs = Environment.MVND_KEEP_ALIVE.asDuration().toMillis();

        SocketFamily socketFamily = Environment.MVND_SOCKET_FAMILY
                .asOptional()
                .map(SocketFamily::valueOf)
                .orElse(SocketFamily.inet);

        try {
            cli = (DaemonCli) getClass()
                    .getClassLoader()
                    .loadClass("org.apache.maven.cli.DaemonMavenCli")
                    .getDeclaredConstructor()
                    .newInstance();
            registry = new DaemonRegistry(Environment.MVND_REGISTRY.asPath());
            socket = socketFamily.openServerSocket();
            executor = Executors.newScheduledThreadPool(1);
            strategy = DaemonExpiration.master();
            memoryStatus = new DaemonMemoryStatus(executor);

            SecureRandom secureRandom = new SecureRandom();
            byte[] token = new byte[DaemonInfo.TOKEN_SIZE];
            secureRandom.nextBytes(token);

            List<String> opts = new ArrayList<>();
            Arrays.stream(Environment.values())
                    .filter(Environment::isDiscriminating)
                    .forEach(
                            envKey -> envKey.asOptional().ifPresent(val -> opts.add(envKey.getProperty() + "=" + val)));
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(opts.stream()
                        .collect(Collectors.joining("\n     ", "Initializing daemon with properties:\n     ", "\n")));
            }
            long cur = System.currentTimeMillis();
            info = new DaemonInfo(
                    daemonId,
                    Environment.MVND_JAVA_HOME.asString(),
                    Environment.MVND_HOME.asString(),
                    DaemonRegistry.getProcessId(),
                    SocketFamily.toString(socket.getLocalAddress()),
                    token,
                    Locale.getDefault().toLanguageTag(),
                    opts,
                    Busy,
                    cur,
                    cur);
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
                        socket.close();
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
            LOGGER.warn("Error clearing cache {}.{}", clazzName, fieldName, t);
        }
    }

    public void run() {
        try {
            Duration expirationCheckDelay = Environment.MVND_EXPIRATION_CHECK_DELAY.asDuration();
            executor.scheduleAtFixedRate(
                    this::expirationCheck,
                    expirationCheckDelay.toMillis(),
                    expirationCheckDelay.toMillis(),
                    TimeUnit.MILLISECONDS);
            LOGGER.info("Daemon started");
            if (noDaemon) {
                try (SocketChannel socket = this.socket.accept()) {
                    client(socket);
                }
            } else {
                new DaemonThread(this::accept).start();
                awaitStop();
            }
        } catch (Throwable t) {
            LOGGER.error("Error running daemon loop", t);
        } finally {
            registry.remove(daemonId);
        }
    }

    static class DaemonThread extends Thread {
        public DaemonThread(Runnable target) {
            super(target);
        }
    }

    private void accept() {
        try {
            while (true) {
                try (SocketChannel socket = this.socket.accept()) {
                    try {
                        // execute the client connection handling inside a new thread to guard against possible
                        // ThreadLocal memory leaks
                        // see https://github.com/apache/maven-mvnd/issues/798 for more details
                        Thread handler = new Thread(() -> client(socket));
                        handler.start();
                        handler.join();
                    } catch (Throwable t) {
                        LOGGER.error("Error handling a client connection", t);
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Error running daemon loop", t);
        }
    }

    private void client(SocketChannel socket) {
        LOGGER.info("Client connected");
        if (!checkToken(socket)) {
            LOGGER.error("Received invalid token, dropping connection");
            updateState(DaemonState.Idle);

            return;
        }

        try (DaemonConnection connection = new DaemonConnection(socket)) {
            LOGGER.info("Waiting for request");
            SynchronousQueue<Message> request = new SynchronousQueue<>();
            new DaemonThread(() -> {
                        Message message = connection.receive();
                        request.offer(message);
                    })
                    .start();
            Message message = request.poll(1, TimeUnit.MINUTES);
            if (message == null) {
                LOGGER.info("Could not receive request after one minute, dropping connection");
                updateState(DaemonState.Idle);
                return;
            }
            LOGGER.info("Request received: {}", message);
            if (message instanceof BuildRequest) {
                handle(connection, (BuildRequest) message);
            }
        } catch (Throwable t) {
            LOGGER.error("Error reading request", t);
        } finally {
            if (!noDaemon) {
                clearCache("sun.net.www.protocol.jar.JarFileFactory", "urlCache");
                clearCache("sun.net.www.protocol.jar.JarFileFactory", "fileCache");
            }
        }
    }

    private boolean checkToken(SocketChannel socket) {
        byte[] token = new byte[info.getToken().length];
        ByteBuffer tokenBuffer = ByteBuffer.wrap(token);

        try {
            do {
                if (socket.read(tokenBuffer) == -1) {
                    break;
                }
            } while (tokenBuffer.remaining() > 0);
        } catch (final IOException e) {
            LOGGER.debug("Discarding EOFException: {}", e.toString(), e);
        }

        return MessageDigest.isEqual(info.getToken(), token);
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
        registry.storeStopEvent(new DaemonStopEvent(daemonId, System.currentTimeMillis(), status, reason));
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
                    LOGGER.debug(
                            "Marking daemon stopped due to {}. The daemon is running a build: {}",
                            reason,
                            state == Busy);
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
            try {
                ProcessHelper.killChildrenProcesses();
            } catch (Throwable t) {
                LOGGER.debug("Error killing children processes", t);
                t.printStackTrace();
            }
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
        final BlockingQueue<Message> sendQueue = new PriorityBlockingQueue<>(64, Message.getMessageComparator());
        final BlockingQueue<Message> recvQueue = new LinkedBlockingDeque<>();
        final BuildEventListener buildEventListener = new ClientDispatcher(sendQueue);
        final DaemonInputStream daemonInputStream =
                new DaemonInputStream(projectId -> sendQueue.add(Message.requestInput(projectId)));
        try (ProjectBuildLogAppender logAppender = new ProjectBuildLogAppender(buildEventListener)) {

            LOGGER.info("Executing request");

            Thread sender = new Thread(() -> {
                try {
                    boolean flushed = true;
                    while (true) {
                        Message m;
                        if (flushed) {
                            m = sendQueue.poll(keepAliveMs, TimeUnit.MILLISECONDS);
                            if (m == null) {
                                m = Message.BareMessage.KEEP_ALIVE_SINGLETON;
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
                        if (m == Message.BareMessage.STOP_SINGLETON) {
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
                        if (message == Message.BareMessage.CANCEL_BUILD_SINGLETON) {
                            updateState(Canceled);
                            return;
                        } else if (message instanceof Message.InputData) {
                            daemonInputStream.addInputData(((Message.InputData) message).getData());
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
                System.setIn(daemonInputStream);
                System.setOut(new LoggingOutputStream(s -> sendQueue.add(Message.out(s))).printStream());
                System.setErr(new LoggingOutputStream(s -> sendQueue.add(Message.err(s))).printStream());
                int exitCode = cli.main(
                        buildRequest.getArgs(),
                        buildRequest.getWorkingDir(),
                        buildRequest.getProjectDir(),
                        buildRequest.getEnv(),
                        buildEventListener);
                LOGGER.info("Build finished, finishing message dispatch");
                buildEventListener.finish(exitCode);
            } catch (Throwable t) {
                LOGGER.error("Error while building project", t);
                buildEventListener.fail(t);
            } finally {
                sender.join();
                ProjectBuildLogAppender.setProjectId(null);
            }
        } catch (Throwable t) {
            LOGGER.error("Error while building project", t);
        } finally {
            if (!noDaemon) {
                LOGGER.info("Daemon back to idle");
                updateState(DaemonState.Idle);
                System.gc();
            }
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

    public String getDaemonId() {
        return info.getId();
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

    static class DaemonInputStream extends InputStream {
        private final Consumer<String> startReadingFromProject;
        private final LinkedList<byte[]> datas = new LinkedList<>();
        private int pos = -1;
        private String projectReading = null;

        DaemonInputStream(Consumer<String> startReadingFromProject) {
            this.startReadingFromProject = startReadingFromProject;
        }

        @Override
        public int available() throws IOException {
            synchronized (datas) {
                String projectId = ProjectBuildLogAppender.getProjectId();
                if (!Objects.equals(projectId, projectReading)) {
                    projectReading = projectId;
                    startReadingFromProject.accept(projectId);
                }
                return datas.stream().mapToInt(a -> a.length).sum() - Math.max(pos, 0);
            }
        }

        @Override
        public int read() throws IOException {
            synchronized (datas) {
                String projectId = ProjectBuildLogAppender.getProjectId();
                if (!Objects.equals(projectId, projectReading)) {
                    projectReading = projectId;
                    startReadingFromProject.accept(projectId);
                    // TODO: start a 10ms timer to turn data off
                }
                for (; ; ) {
                    if (datas.isEmpty()) {
                        try {
                            datas.wait();
                        } catch (InterruptedException e) {
                            throw new InterruptedIOException("Interrupted");
                        }
                        pos = -1;
                        continue;
                    }
                    byte[] curData = datas.getFirst();
                    if (pos >= curData.length) {
                        datas.removeFirst();
                        pos = -1;
                        continue;
                    }
                    if (pos < 0) {
                        pos = 0;
                    }
                    return curData[pos++];
                }
            }
        }

        public void addInputData(String data) {
            synchronized (datas) {
                datas.add(data.getBytes(Charset.forName(System.getProperty("file.encoding"))));
                datas.notifyAll();
            }
        }
    }
}
