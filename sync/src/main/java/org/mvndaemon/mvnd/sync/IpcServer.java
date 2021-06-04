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
package org.mvndaemon.mvnd.sync;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mvndaemon.mvnd.sync.IpcMessages.REQUEST_ACQUIRE;
import static org.mvndaemon.mvnd.sync.IpcMessages.REQUEST_CLOSE;
import static org.mvndaemon.mvnd.sync.IpcMessages.REQUEST_CONTEXT;
import static org.mvndaemon.mvnd.sync.IpcMessages.RESPONSE_ACQUIRE;
import static org.mvndaemon.mvnd.sync.IpcMessages.RESPONSE_CLOSE;
import static org.mvndaemon.mvnd.sync.IpcMessages.RESPONSE_CONTEXT;

/**
 * Implementation of the server side.
 * The server instance is bound to a given maven repository.
 */
public class IpcServer {

    static final long IDLE_TIMEOUT = TimeUnit.SECONDS.toNanos(60);

    private final ServerSocket serverSocket;
    private final AtomicInteger clients = new AtomicInteger();
    private final AtomicInteger counter = new AtomicInteger();
    private final Map<String, Lock> locks = new ConcurrentHashMap<>();
    private final Map<String, Context> contexts = new ConcurrentHashMap<>();

    private volatile long lastUsed;
    private volatile boolean closing;

    public IpcServer() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    }

    public static void main(String[] args) throws Exception {
        // When spawning a new process, the child process is create within
        // the same process group.  This means that a few signals are sent
        // to the whole group.  This is the case for SIGINT (Ctrl-C) and
        // SIGTSTP (Ctrl-Z) which are both sent to all the processed in the
        // group when initiated from the controlling terminal.
        // This is only a problem when the client creates the daemon, but
        // without ignoring those signals, a client being interrupted will
        // also interrupt and kill the daemon.
        try {
            sun.misc.Signal.handle(new sun.misc.Signal("INT"), sun.misc.SignalHandler.SIG_IGN);
            if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
                sun.misc.Signal.handle(new sun.misc.Signal("TSTP"), sun.misc.SignalHandler.SIG_IGN);
            }
        } catch (Throwable t) {
            System.err.println("Unable to ignore INT and TSTP signals");
            t.printStackTrace();
        }

        int tmpPort = Integer.parseInt(args[0]);
        int rand = Integer.parseInt(args[1]);

        IpcServer server = new IpcServer();
        run(server::run);
        int port = server.getPort();

        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), tmpPort));
            try (DataOutputStream dos = new DataOutputStream(s.getOutputStream())) {
                dos.writeInt(rand);
                dos.writeInt(port);
                dos.flush();
            }
        }
    }

    private static void debug(String msg, Object... args) {
        //System.out.printf("[ipc] [debug] " + msg + "\n", args);
    }

    private static void info(String msg, Object... args) {
        System.out.printf("[ipc] [info] " + msg + "\n", args);
    }

    private static void error(String msg, Throwable t) {
        System.out.println("[ipc] [error] " + msg);
        t.printStackTrace(System.out);
    }

    private static void run(Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.start();
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public void run() {
        try {
            info("IpcServer started on port %d", getPort());
            use();
            run(this::expirationCheck);
            while (!closing) {
                Socket socket = this.serverSocket.accept();
                run(() -> client(socket));
            }
        } catch (Throwable t) {
            if (!closing) {
                error("Error running sync server loop", t);
            }
        }
    }

    private void client(Socket socket) {
        int c = clients.incrementAndGet();
        info("New client connected (%d connected)", c);
        use();
        Map<String, Context> clientContexts = new ConcurrentHashMap<>();
        try {
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            while (!closing) {
                int requestId = input.readInt();
                int sz = input.readInt();
                List<String> request = new ArrayList<>(sz);
                for (int i = 0; i < sz; i++) {
                    request.add(input.readUTF());
                }
                if (request.isEmpty()) {
                    throw new IOException("Received invalid request");
                }
                use();
                String contextId;
                Context context;
                String command = request.remove(0);
                switch (command) {
                case REQUEST_CONTEXT:
                    if (request.size() != 1) {
                        throw new IOException("Expected one argument for " + command + " but got " + request);
                    }
                    boolean shared = Boolean.parseBoolean(request.remove(0));
                    context = new Context(shared);
                    contexts.put(context.id, context);
                    clientContexts.put(context.id, context);
                    synchronized (output) {
                        debug("Created context %s", context.id);
                        output.writeInt(requestId);
                        output.writeInt(2);
                        output.writeUTF(RESPONSE_CONTEXT);
                        output.writeUTF(context.id);
                        output.flush();
                    }
                    break;
                case REQUEST_ACQUIRE:
                    if (request.size() < 1) {
                        throw new IOException("Expected at least one argument for " + command + " but got " + request);
                    }
                    contextId = request.remove(0);
                    context = contexts.get(contextId);
                    if (context == null) {
                        throw new IOException("Unknown context: " + contextId + ". Known contexts = " + contexts.keySet());
                    }
                    context.lock(request).thenRun(() -> {
                        try {
                            synchronized (output) {
                                debug("Locking in context %s", context.id);
                                output.writeInt(requestId);
                                output.writeInt(1);
                                output.writeUTF(RESPONSE_ACQUIRE);
                                output.flush();
                            }
                        } catch (IOException e) {
                            try {
                                socket.close();
                            } catch (IOException ioException) {
                                e.addSuppressed(ioException);
                            }
                            error("Error writing lock response", e);
                        }
                    });
                    break;
                case REQUEST_CLOSE:
                    if (request.size() != 1) {
                        throw new IOException("Expected one argument for " + command + " but got " + request);
                    }
                    contextId = request.remove(0);
                    context = contexts.remove(contextId);
                    clientContexts.remove(contextId);
                    if (context == null) {
                        throw new IOException("Unknown context: " + contextId + ". Known contexts = " + contexts.keySet());
                    }
                    context.unlock();
                    synchronized (output) {
                        debug("Closing context %s", context.id);
                        output.writeInt(requestId);
                        output.writeInt(1);
                        output.writeUTF(RESPONSE_CLOSE);
                        output.flush();
                    }
                    break;
                default:
                    throw new IOException("Unknown request: " + request.get(0));
                }
            }
        } catch (Throwable t) {
            error("Error processing request", t);
        } finally {
            info("Client disconnecting...");
            clientContexts.values().forEach(context -> {
                contexts.remove(context.id);
                context.unlock();
            });
            try {
                socket.close();
            } catch (IOException ioException) {
                // ignore
            }
            info("%d clients left", clients.decrementAndGet());
        }
    }

    private void use() {
        lastUsed = System.nanoTime();
    }

    private void expirationCheck() {
        while (true) {
            long current = System.nanoTime();
            if (current - lastUsed > IDLE_TIMEOUT) {
                close();
                break;
            }
        }
    }

    private void close() {
        closing = true;
        try {
            serverSocket.close();
        } catch (IOException e) {
            error("Error closing server socket", e);
        }
    }

    static class Waiter {
        final Context context;
        final CompletableFuture<Void> future;

        Waiter(Context context, CompletableFuture<Void> future) {
            this.context = context;
            this.future = future;
        }
    }

    static class Lock {

        final String key;

        List<Context> holders;
        List<Waiter> waiters;

        Lock(String key) {
            this.key = key;
        }

        public synchronized CompletableFuture<Void> lock(Context context) {
            if (holders == null) {
                holders = new ArrayList<>();
            }
            if (holders.isEmpty() || holders.get(0).shared && context.shared) {
                holders.add(context);
                return CompletableFuture.completedFuture(null);
            }
            if (waiters == null) {
                waiters = new ArrayList<>();
            }

            CompletableFuture<Void> future = new CompletableFuture<>();
            waiters.add(new Waiter(context, future));
            return future;
        }

        public synchronized void unlock(Context context) {
            if (holders.remove(context)) {
                while (waiters != null && !waiters.isEmpty()
                        && (holders.isEmpty() || holders.get(0).shared && waiters.get(0).context.shared)) {
                    Waiter waiter = waiters.remove(0);
                    holders.add(waiter.context);
                    waiter.future.complete(null);
                }
            } else if (waiters != null) {
                for (Iterator<Waiter> it = waiters.iterator(); it.hasNext();) {
                    Waiter waiter = it.next();
                    if (waiter.context == context) {
                        it.remove();
                        waiter.future.cancel(false);
                    }
                }
            }
        }

    }

    class Context {

        final String id;
        final boolean shared;
        final List<String> locks = new CopyOnWriteArrayList<>();

        Context(boolean shared) {
            this.id = String.format("%08x", counter.incrementAndGet());
            this.shared = shared;
        }

        public CompletableFuture<?> lock(List<String> keys) {
            locks.addAll(keys);
            CompletableFuture<?>[] futures = keys.stream()
                    .map(k -> IpcServer.this.locks.computeIfAbsent(k, Lock::new))
                    .map(l -> l.lock(this))
                    .toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(futures);
        }

        public void unlock() {
            locks.stream()
                    .map(k -> IpcServer.this.locks.computeIfAbsent(k, Lock::new))
                    .forEach(l -> l.unlock(this));
        }
    }

}
