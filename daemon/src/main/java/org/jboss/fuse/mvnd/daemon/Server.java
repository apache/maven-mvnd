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
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.maven.cli.CliRequest;
import org.apache.maven.cli.CliRequestBuilder;
import org.apache.maven.cli.DaemonMavenCli;
import org.jboss.fuse.mvnd.common.DaemonConnection;
import org.jboss.fuse.mvnd.common.DaemonException;
import org.jboss.fuse.mvnd.common.DaemonExpirationStatus;
import org.jboss.fuse.mvnd.common.DaemonInfo;
import org.jboss.fuse.mvnd.common.DaemonRegistry;
import org.jboss.fuse.mvnd.common.DaemonState;
import org.jboss.fuse.mvnd.common.DaemonStopEvent;
import org.jboss.fuse.mvnd.common.Environment;
import org.jboss.fuse.mvnd.common.Layout;
import org.jboss.fuse.mvnd.common.Message;
import org.jboss.fuse.mvnd.common.Message.BuildEvent;
import org.jboss.fuse.mvnd.common.Message.BuildEvent.Type;
import org.jboss.fuse.mvnd.common.Message.BuildException;
import org.jboss.fuse.mvnd.common.Message.BuildMessage;
import org.jboss.fuse.mvnd.common.Message.BuildRequest;
import org.jboss.fuse.mvnd.common.Message.MessageSerializer;
import org.jboss.fuse.mvnd.daemon.DaemonExpiration.DaemonExpirationResult;
import org.jboss.fuse.mvnd.daemon.DaemonExpiration.DaemonExpirationStrategy;
import org.jboss.fuse.mvnd.logging.smart.AbstractLoggingSpy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jboss.fuse.mvnd.common.DaemonState.Busy;
import static org.jboss.fuse.mvnd.common.DaemonState.StopRequested;
import static org.jboss.fuse.mvnd.common.DaemonState.Stopped;

public class Server implements AutoCloseable, Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);
    public static final int CANCEL_TIMEOUT = 10 * 1000;
    public static final int DEFAULT_IDLE_TIMEOUT = (int) TimeUnit.HOURS.toMillis(3);

    private final String uid;
    private final ServerSocketChannel socket;
    private final DaemonMavenCli cli;
    private volatile DaemonInfo info;
    private final DaemonRegistry registry;
    private final Layout layout;

    private final ScheduledExecutorService executor;
    private final DaemonExpirationStrategy strategy;
    private final Lock expirationLock = new ReentrantLock();
    private final Lock stateLock = new ReentrantLock();
    private final Condition condition = stateLock.newCondition();

    public Server(String uid) throws IOException {
        this.uid = uid;
        this.layout = Layout.getEnvInstance();
        try {
            cli = new DaemonMavenCli();

            registry = new DaemonRegistry(layout.registry());
            socket = ServerSocketChannel.open().bind(new InetSocketAddress(0));

            final int idleTimeout = Environment.DAEMON_IDLE_TIMEOUT
                    .systemProperty()
                    .orDefault(() -> String.valueOf(DEFAULT_IDLE_TIMEOUT))
                    .asInt();
            executor = Executors.newScheduledThreadPool(1);
            strategy = DaemonExpiration.master();

            List<String> opts = new ArrayList<>();
            long cur = System.currentTimeMillis();
            final Path javaHome = Paths.get(System.getProperty("mvnd.java.home"));
            info = new DaemonInfo(uid, javaHome.toString(), layout.mavenHome().toString(),
                    DaemonRegistry.getProcessId(), socket.socket().getLocalPort(),
                    idleTimeout, Locale.getDefault().toLanguageTag(), opts,
                    Busy, cur, cur);
            registry.store(info);
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize " + Server.class.getName(), e);
        }
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
            executor.scheduleAtFixedRate(this::expirationCheck,
                    info.getIdleTimeout(), info.getIdleTimeout(), TimeUnit.MILLISECONDS);
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
                SocketChannel socket = this.socket.accept();
                new DaemonThread(() -> client(socket)).start();
            }
        } catch (Throwable t) {
            LOGGER.error("Error running daemon loop", t);
        }
    }

    private void client(SocketChannel socket) {
        LOGGER.info("Client connected");
        try (DaemonConnection<Message> connection = new DaemonConnection<Message>(socket, new MessageSerializer())) {
            while (true) {
                LOGGER.info("Waiting for request");
                Message message = connection.receive();
                if (message == null) {
                    return;
                }
                LOGGER.info("Request received: " + message);

                if (message instanceof BuildRequest) {
                    handle(connection, (BuildRequest) message);
                }
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
                        LOGGER.debug("cancel requested.");
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

        //        LOGGER.debug("Cancel requested: will wait for daemon to become idle.");
        //        try {
        //            cancellationToken.cancel();
        //        } catch (Exception ex) {
        //            LOGGER.error("Cancel processing failed. Will continue.", ex);
        //        }

        stateLock.lock();
        try {
            long rem;
            while ((rem = System.currentTimeMillis() - time) > 0) {
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
        }
    }

    static final Message STOP = new Message() {
    };

    private void handle(DaemonConnection<Message> connection, BuildRequest buildRequest) {
        updateState(Busy);
        try {
            LOGGER.info("Executing request");
            CliRequest req = new CliRequestBuilder()
                    .arguments(buildRequest.getArgs())
                    .workingDirectory(Paths.get(buildRequest.getWorkingDir()))
                    .projectDirectory(Paths.get(buildRequest.getProjectDir()))
                    .build();

            PriorityBlockingQueue<Message> queue = new PriorityBlockingQueue<Message>(64,
                    Comparator.comparingInt(this::getClassOrder).thenComparingLong(Message::timestamp));

            DaemonLoggingSpy loggingSpy = new DaemonLoggingSpy(queue);
            AbstractLoggingSpy.instance(loggingSpy);
            Thread pumper = new Thread(() -> {
                try {
                    while (true) {
                        Message m = queue.poll();
                        if (m == null) {
                            connection.flush();
                            m = queue.take();
                        }
                        if (m == STOP) {
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
            pumper.start();
            try {
                cli.doMain(req);
                LOGGER.info("Build finished, finishing message dispatch");
                loggingSpy.finish();
            } catch (Throwable t) {
                LOGGER.error("Error while building project", t);
                loggingSpy.fail(t);
            } finally {
                pumper.join();
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
        if (m instanceof BuildRequest) {
            return 0;
        } else if (m instanceof BuildEvent || m instanceof BuildMessage) {
            return 1;
        } else if (m instanceof BuildException) {
            return 97;
        } else if (m == STOP) {
            return 99;
        } else {
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

    public int getIdleTimeout() {
        return info.getIdleTimeout();
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
        private final PriorityBlockingQueue<Message> queue;

        public DaemonLoggingSpy(PriorityBlockingQueue<Message> queue) {
            this.queue = queue;
        }

        @Override
        public void init(Context context) throws Exception {
            super.init(context);
            queue.add(new BuildEvent(Type.BuildStarted, "", ""));
        }

        @Override
        public void close() throws Exception {
            super.close();
        }

        public void finish() throws Exception {
            queue.add(new BuildEvent(Type.BuildStopped, "", ""));
            queue.add(STOP);
        }

        public void fail(Throwable t) throws Exception {
            queue.add(new BuildException(t));
            queue.add(STOP);
        }

        @Override
        protected void onStartProject(String projectId, String display) {
            super.onStartProject(projectId, display);
            sendEvent(Type.ProjectStarted, projectId, display);
        }

        @Override
        protected void onStopProject(String projectId, String display) {
            sendEvent(Type.ProjectStopped, projectId, display);
            super.onStopProject(projectId, display);
        }

        @Override
        protected void onStartMojo(String projectId, String display) {
            super.onStartMojo(projectId, display);
            sendEvent(Type.MojoStarted, projectId, display);
        }

        @Override
        protected void onStopMojo(String projectId, String display) {
            sendEvent(Type.MojoStopped, projectId, display);
            super.onStopMojo(projectId, display);
        }

        @Override
        protected void onProjectLog(String projectId, String message) {
            queue.add(new BuildMessage(projectId, message));
            super.onProjectLog(projectId, message);
        }

        private void sendEvent(Type type, String projectId, String display) {
            queue.add(new BuildEvent(type, projectId, display));
        }

    }
}
