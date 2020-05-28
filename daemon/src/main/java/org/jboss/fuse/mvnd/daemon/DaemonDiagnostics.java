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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collector;

public class DaemonDiagnostics {

    private final static int TAIL_SIZE = 20;

    private final String uid;
    private final Path daemonLog;

    public DaemonDiagnostics(String uid, Path daemonLog) {
        this.uid = uid;
        this.daemonLog = daemonLog;
    }

    @Override
    public String toString() {
        return "{"
                + "uid=" + uid
                + ", daemonLog=" + daemonLog
                + '}';
    }

    private String tailDaemonLog() {
        try {
            String tail = tail(daemonLog, TAIL_SIZE);
            return formatTail(tail);
        } catch (IOException e) {
            return "Unable to read from the daemon log file: " + daemonLog + ", because of: " + e.getCause();
        }
    }

    /**
     * @param path to read from tail
     * @param maxLines max lines to read
     * @return tail content
     * @throws IOException when reading failed
     */
    static String tail(Path path, int maxLines) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(path)) {
            return String.join("\n", r.lines().collect(lastN(maxLines)));
        }
    }

    static <T> Collector<T, ?, List<T>> lastN(int n) {
        return Collector.<T, Deque<T>, List<T>>of(ArrayDeque::new, (acc, t) -> {
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

    private String formatTail(String tail) {
        return "----- Last  " + TAIL_SIZE + " lines from daemon log file - " + daemonLog + " -----\n"
                + tail
                + "----- End of the daemon log -----\n";
    }

    public String describe() {
        return "Daemon uid: " + uid + "\n"
                + "  log file: " + daemonLog + "\n"
                + tailDaemonLog();
    }
}
