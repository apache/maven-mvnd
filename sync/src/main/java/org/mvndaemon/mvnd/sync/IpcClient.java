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

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;
import java.nio.channels.FileLock;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mvndaemon.mvnd.sync.IpcMessages.REQUEST_ACQUIRE;
import static org.mvndaemon.mvnd.sync.IpcMessages.REQUEST_CLOSE;
import static org.mvndaemon.mvnd.sync.IpcMessages.REQUEST_CONTEXT;
import static org.mvndaemon.mvnd.sync.IpcMessages.RESPONSE_ACQUIRE;
import static org.mvndaemon.mvnd.sync.IpcMessages.RESPONSE_CLOSE;
import static org.mvndaemon.mvnd.sync.IpcMessages.RESPONSE_CONTEXT;
import static org.mvndaemon.mvnd.sync.IpcServer.FAMILY_PROP;

/**
 * Client side implementation.
 * The client instance is bound to a given maven repository.
 */
public class IpcClient {

    Path repository;
    Path syncServerPath;
    SocketChannel socket;
    DataOutputStream output;
    DataInputStream input;
    Thread receiver;
    AtomicInteger requestId = new AtomicInteger();
    Map<Integer, CompletableFuture<List<String>>> responses = new ConcurrentHashMap<>();

    IpcClient(Path repository, Path syncServerPath) {
        this.repository = repository;
        this.syncServerPath = syncServerPath;
    }

    synchronized void ensureInitialized() throws IOException {
        if (socket == null) {
            socket = createClient();
            ByteChannel wrapper = SocketHelper.wrapChannel(socket);
            input = new DataInputStream(Channels.newInputStream(wrapper));
            output = new DataOutputStream(Channels.newOutputStream(wrapper));
            receiver = new Thread(this::receive);
            receiver.setDaemon(true);
            receiver.start();
        }
    }

    SocketChannel createClient() throws IOException {
        String familyProp = System.getProperty(FAMILY_PROP);
        StandardProtocolFamily family = familyProp != null
                ? StandardProtocolFamily.valueOf(familyProp)
                : JavaVersion.getJavaSpec() >= 16.0f ? StandardProtocolFamily.UNIX : StandardProtocolFamily.INET;

        Path lockFile = repository.resolve(".maven-resolver-ipc-lock-" + family.name().toLowerCase())
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(lockFile)) {
            if (!Files.isDirectory(lockFile.getParent())) {
                Files.createDirectories(lockFile.getParent());
            }
        }

        try (RandomAccessFile raf = new RandomAccessFile(lockFile.toFile(), "rw")) {
            try (FileLock lock = raf.getChannel().lock()) {
                String line = raf.readLine();
                if (line != null) {
                    try {
                        SocketAddress address = SocketHelper.socketAddressFromString(line);
                        return SocketChannel.open(address);
                    } catch (IOException e) {
                        // ignore
                    }
                }

                ServerSocketChannel ss = SocketHelper.openServerSocket(family);
                String tmpaddr = SocketHelper.socketAddressToString(ss.getLocalAddress());
                String rand = Long.toHexString(new Random().nextLong());

                String noNative = System.getProperty(IpcServer.NO_NATIVE_PROP);
                Closeable close;
                if (Boolean.parseBoolean(noNative)) {
                    String noFork = System.getProperty(IpcServer.NO_FORK_PROP);
                    if (Boolean.parseBoolean(noFork)) {
                        IpcServer server = IpcServer.runServer(family, tmpaddr, rand);
                        close = server::close;
                    } else {
                        List<String> args = new ArrayList<>();
                        String javaHome = System.getenv("JAVA_HOME");
                        if (javaHome == null) {
                            javaHome = System.getProperty("java.home");
                        }
                        boolean win = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
                        String javaCmd = win ? "bin\\java.exe" : "bin/java";
                        String java = Paths.get(javaHome).resolve(javaCmd).toAbsolutePath().toString();
                        args.add(java);
                        String classpath;
                        String className = getClass().getName().replace('.', '/') + ".class";
                        String url = getClass().getClassLoader().getResource(className).toString();
                        if (url.startsWith("jar:")) {
                            classpath = url.substring("jar:".length(), url.indexOf("!/"));
                        } else if (url.startsWith("file:")) {
                            classpath = url.substring("file:".length(), url.indexOf(className));
                        } else {
                            throw new IllegalStateException();
                        }
                        args.add("-cp");
                        args.add(classpath);
                        String timeout = System.getProperty(IpcServer.IDLE_TIMEOUT_PROP);
                        if (timeout != null) {
                            args.add("-D" + IpcServer.IDLE_TIMEOUT_PROP + "=" + timeout);
                        }
                        args.add(IpcServer.class.getName());
                        args.add(family.name());
                        args.add(tmpaddr);
                        args.add(rand);
                        ProcessBuilder processBuilder = new ProcessBuilder();
                        ProcessBuilder.Redirect discard = ProcessBuilder.Redirect.to(new File(win ? "NUL" : "/dev/null"));
                        discard = ProcessBuilder.Redirect.INHERIT;
                        Process process = processBuilder
                                .directory(lockFile.getParent().toFile())
                                .command(args)
                                .redirectOutput(discard)
                                .redirectError(discard)
                                .start();
                        close = process::destroyForcibly;
                    }
                } else {
                    List<String> args = new ArrayList<>();
                    boolean win = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
                    String syncCmd = win ? "mvnd-sync.exe" : "mvnd-sync";
                    args.add(syncServerPath.resolve(syncCmd).toString());
                    String timeout = System.getProperty(IpcServer.IDLE_TIMEOUT_PROP);
                    if (timeout != null) {
                        args.add("-D" + IpcServer.IDLE_TIMEOUT_PROP + "=" + timeout);
                    }
                    args.add(family.name());
                    args.add(tmpaddr);
                    args.add(rand);
                    ProcessBuilder processBuilder = new ProcessBuilder();
                    ProcessBuilder.Redirect discard = ProcessBuilder.Redirect.to(new File(win ? "NUL" : "/dev/null"));
                    discard = ProcessBuilder.Redirect.INHERIT;
                    Process process = processBuilder
                            .directory(lockFile.getParent().toFile())
                            .command(args)
                            .redirectOutput(discard)
                            .redirectError(discard)
                            .start();
                    close = process::destroyForcibly;
                }

                ExecutorService es = Executors.newSingleThreadExecutor();
                Future<String[]> future = es.submit(() -> {
                    SocketChannel s = ss.accept();
                    DataInputStream dis = new DataInputStream(Channels.newInputStream(s));
                    String rand2 = dis.readUTF();
                    String addr2 = dis.readUTF();
                    return new String[] { rand2, addr2 };
                });
                String[] res;
                try {
                    res = future.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    close.close();
                    throw e;
                } finally {
                    es.shutdownNow();
                    ss.close();
                }
                if (!Objects.equals(rand, res[0])) {
                    close.close();
                    throw new IllegalStateException("IpcServer did not respond with the correct random");
                }

                SocketAddress addr = SocketHelper.socketAddressFromString(res[1]);
                SocketChannel socket = SocketChannel.open(addr);

                raf.seek(0);
                raf.writeBytes(res[1] + "\n");
                return socket;
            } catch (Exception e) {
                throw new RuntimeException("Unable to create and connect to lock server", e);
            }
        }
    }

    void receive() {
        try {
            while (true) {
                int id = input.readInt();
                int sz = input.readInt();
                List<String> s = new ArrayList<>(sz);
                for (int i = 0; i < sz; i++) {
                    s.add(input.readUTF());
                }
                CompletableFuture<List<String>> f = responses.remove(id);
                if (f == null || s.isEmpty()) {
                    throw new IllegalStateException("Protocol error");
                }
                f.complete(s);
            }
        } catch (Exception e) {
            close(e);
        }
    }

    List<String> send(List<String> request) throws IOException {
        ensureInitialized();
        int id = requestId.incrementAndGet();
        CompletableFuture<List<String>> response = new CompletableFuture<>();
        responses.put(id, response);
        synchronized (output) {
            output.writeInt(id);
            output.writeInt(request.size());
            for (String s : request) {
                output.writeUTF(s);
            }
            output.flush();
        }
        try {
            return response.get();
        } catch (InterruptedException e) {
            throw (IOException) new InterruptedIOException("Interrupted").initCause(e);
        } catch (ExecutionException e) {
            throw new IOException("Execution error", e);
        }
    }

    void close() {
        close(new IOException("Closing"));
    }

    synchronized void close(Throwable e) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException t) {
                e.addSuppressed(t);
            }
            socket = null;
            input = null;
            output = null;
        }
        if (receiver != null) {
            receiver.interrupt();
            try {
                receiver.join(1000);
            } catch (InterruptedException t) {
                e.addSuppressed(t);
            }
        }
        responses.values().forEach(f -> f.completeExceptionally(e));
        responses.clear();
    }

    String newContext(boolean shared) {
        RuntimeException error = new RuntimeException("Unable to create new sync context");
        for (int i = 0; i < 2; i++) {
            try {
                List<String> response = send(Arrays.asList(
                        REQUEST_CONTEXT, Boolean.toString(shared)));
                if (response.size() != 2 || !RESPONSE_CONTEXT.equals(response.get(0))) {
                    throw new IOException("Unexpected response: " + response);
                }
                return response.get(1);
            } catch (Exception e) {
                close(e);
                error.addSuppressed(e);
            }
        }
        throw error;
    }

    void lock(String contextId, Collection<String> keys) {
        try {
            List<String> req = new ArrayList<>(keys.size() + 2);
            req.add(REQUEST_ACQUIRE);
            req.add(contextId);
            req.addAll(keys);
            List<String> response = send(req);
            if (response.size() != 1 || !RESPONSE_ACQUIRE.equals(response.get(0))) {
                throw new IOException("Unexpected response: " + response);
            }
        } catch (Exception e) {
            close(e);
            throw new RuntimeException("Unable to perform lock (contextId = " + contextId + ")", e);
        }
    }

    void unlock(String contextId) {
        try {
            List<String> response = send(Arrays.asList(REQUEST_CLOSE, contextId));
            if (response.size() != 1 || !RESPONSE_CLOSE.equals(response.get(0))) {
                throw new IOException("Unexpected response: " + response);
            }
        } catch (Exception e) {
            close(e);
            throw new RuntimeException("Unable to unlock (contextId = " + contextId + ")", e);
        }
    }

    @Override
    public String toString() {
        return "IpcClient{"
                + "repository=" + repository + ','
                + "address=" + (socket != null ? getAddress() : 0)
                + '}';
    }

    private String getAddress() {
        try {
            return SocketHelper.socketAddressToString(socket.getLocalAddress());
        } catch (IOException e) {
            return "[not bound]";
        }
    }

}
