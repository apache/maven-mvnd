/*
 * Copyright 2009 the original author or authors.
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
package org.jboss.fuse.mvnd.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collector;

/**
 * File origin:
 * https://github.com/gradle/gradle/blob/v5.6.2/subprojects/launcher/src/main/java/org/gradle/launcher/daemon/diagnostics/DaemonDiagnostics.java
 */
public class DaemonDiagnostics {

    private final static int TAIL_SIZE = 20;

    private final String uid;
    private final Layout layout;

    public DaemonDiagnostics(String uid, Layout layout) {
        this.uid = uid;
        this.layout = layout;
    }

    @Override
    public String toString() {
        return "{"
                + "uid=" + uid
                + ", layout=" + layout
                + '}';
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("Daemon uid: ").append(uid).append("\n");
        tail(sb, "log file", layout.daemonLog(uid));
        tail(sb, "output", layout.daemonOutLog(uid));
        return sb.toString();
    }

    static void tail(StringBuilder sb, String name, Path log) {
        try {
            String tail = tail(log);
            sb.append("  ").append(name).append(": ").append(log).append("\n");
            sb.append("----- Last  " + TAIL_SIZE + " lines from daemon ").append(name).append(" - ").append(log).append(" -----\n");
            sb.append(tail);
            sb.append("----- End of the daemon ").append(name).append(" -----\n");
        } catch (NoSuchFileException e) {
            sb.append("  no ").append(name).append(" at: ").append(log).append("\n");
        } catch (IOException e) {
            sb.append("  unable to read from the daemon ").append(name).append(": ").append(log).append(", because of: ").append(e);
        }
    }

    /**
     * @param  path        to read from tail
     * @return             tail content
     * @throws IOException when reading failed
     */
    static String tail(Path path) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(path)) {
            return String.join("\n", r.lines().collect(lastN(TAIL_SIZE))) + "\n";
        }
    }

    static <T> Collector<T, ?, List<T>> lastN(int n) {
        return Collector.<T, Deque<T>, List<T>> of(ArrayDeque::new, (acc, t) -> {
            if (acc.size() == n)
                acc.pollFirst();
            acc.add(t);
        }, (acc1, acc2) -> {
            while (acc2.size() < n && !acc1.isEmpty()) {
                acc2.addFirst(acc1.pollLast());
            }
            return acc2;
        }, ArrayList::new);
    }

}
