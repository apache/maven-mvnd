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
package org.mvndaemon.mvnd.client;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.mvndaemon.mvnd.common.TimeUtils;

import static org.mvndaemon.mvnd.client.DaemonParameters.LOG_EXTENSION;

/**
 * Deletes old daemon logs. Kept apart from {@code DefaultClient}, which lives in a separate source root that the
 * unit tests cannot see.
 */
final class LogPurger {

    private LogPurger() {}

    /**
     * Deletes the old logs in {@code storage}, except those of the daemons in {@code activeDaemons}, whose log
     * files are still open.
     *
     * @return a message describing what was purged, or {@code null} if there was nothing to report
     */
    static String purge(Path storage, Duration purgeLogPeriod, Set<String> activeDaemons) {
        if (!Files.isDirectory(storage) || !TimeUtils.isPositive(purgeLogPeriod)) {
            return null;
        }
        String date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());
        Path log = storage.resolve("purge-" + date + ".log");
        List<Path> deleted = new ArrayList<>();
        List<Throwable> exceptions = new ArrayList<>();
        FileTime limit = FileTime.from(Instant.now().minus(purgeLogPeriod));
        try (Stream<Path> storagePath = Files.list(storage)) {
            storagePath
                    .filter(p -> p.getFileName().toString().endsWith(LOG_EXTENSION))
                    .filter(p -> !log.equals(p))
                    .filter(p -> !isActiveDaemonLog(p, activeDaemons))
                    .filter(p -> {
                        try {
                            FileTime lmt = Files.getLastModifiedTime(p);
                            return lmt.compareTo(limit) < 0;
                        } catch (IOException e) {
                            exceptions.add(e);
                            return false;
                        }
                    })
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                            deleted.add(p);
                        } catch (IOException e) {
                            exceptions.add(e);
                        }
                    });
        } catch (Exception e) {
            exceptions.add(e);
        }
        if (exceptions.isEmpty() && deleted.isEmpty()) {
            return null;
        }
        String logMessage;
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(
                log, StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.CREATE))) {
            w.printf("Purge executed at %s%n", Instant.now().toString());
            if (deleted.isEmpty()) {
                w.printf("No files deleted.%n");
            } else {
                w.printf("Deleted files:%n");
                for (Path p : deleted) {
                    w.printf("    %s%n", p.toString());
                }
            }
            if (!exceptions.isEmpty()) {
                w.printf("%d exception(s) occurred during the purge", exceptions.size());
                for (Throwable t : exceptions) {
                    t.printStackTrace(w);
                }
            }
            char[] buf = new char[80];
            Arrays.fill(buf, '=');
            w.printf("%s%n", new String(buf));
            logMessage = "log available in " + log.toString();
        } catch (IOException e) {
            logMessage = "an exception occurred when writing log to " + log.toString() + ": " + e.toString();
        }
        if (exceptions.isEmpty()) {
            return String.format("Purged %d log files (%s)", deleted.size(), logMessage);
        } else {
            return String.format(
                    "Purged %d log files with %d exceptions (%s)", deleted.size(), exceptions.size(), logMessage);
        }
    }

    private static boolean isActiveDaemonLog(Path p, Set<String> activeDaemons) {
        String name = p.getFileName().toString();
        if (!name.startsWith("daemon-")) {
            return false;
        }
        // daemon-<id>.log and daemon-<id>.out.log
        String id = name.substring("daemon-".length(), name.length() - LOG_EXTENSION.length());
        if (id.endsWith(".out")) {
            id = id.substring(0, id.length() - ".out".length());
        }
        return activeDaemons.contains(id);
    }
}
