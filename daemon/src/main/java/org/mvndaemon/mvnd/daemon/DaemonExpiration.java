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

import javax.management.Attribute;
import javax.management.ObjectName;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.mvndaemon.mvnd.common.DaemonCompatibilitySpec;
import org.mvndaemon.mvnd.common.DaemonCompatibilitySpec.Result;
import org.mvndaemon.mvnd.common.DaemonExpirationStatus;
import org.mvndaemon.mvnd.common.DaemonInfo;
import org.mvndaemon.mvnd.common.DaemonState;
import org.mvndaemon.mvnd.common.Environment;
import org.mvndaemon.mvnd.common.Os;
import org.mvndaemon.mvnd.common.TimeUtils;
import org.mvndaemon.mvnd.nativ.CLibrary;

import static org.mvndaemon.mvnd.common.DaemonExpirationStatus.DO_NOT_EXPIRE;
import static org.mvndaemon.mvnd.common.DaemonExpirationStatus.GRACEFUL_EXPIRE;
import static org.mvndaemon.mvnd.common.DaemonExpirationStatus.IMMEDIATE_EXPIRE;
import static org.mvndaemon.mvnd.common.DaemonExpirationStatus.QUIET_EXPIRE;
import static org.mvndaemon.mvnd.daemon.DaemonExpiration.DaemonExpirationResult.NOT_TRIGGERED;

/**
 * File origin:
 * https://github.com/gradle/gradle/blob/v5.6.2/subprojects/launcher/src/main/java/org/gradle/launcher/daemon/server/MasterExpirationStrategy.java
 */
public class DaemonExpiration {

    public interface DaemonExpirationStrategy {

        DaemonExpirationResult checkExpiration(Server daemon);
    }

    public static DaemonExpirationStrategy master() {
        return any(
                any(gcTrashing(), lowHeapSpace(), lowNonHeap()),
                all(compatible(), duplicateGracePeriod(), notMostRecentlyUsed()),
                idleTimeout(Environment.MVND_IDLE_TIMEOUT.asDuration()),
                all(duplicateGracePeriod(), notMostRecentlyUsed(), lowMemory(0.05)),
                registryUnavailable());
    }

    static DaemonExpirationStrategy gcTrashing() {
        return daemon -> daemon.getMemoryStatus().isTrashing()
                ? new DaemonExpirationResult(IMMEDIATE_EXPIRE, "JVM garbage collector thrashing")
                : NOT_TRIGGERED;
    }

    static DaemonExpirationStrategy lowHeapSpace() {
        return daemon -> daemon.getMemoryStatus().isHeapSpaceExhausted()
                ? new DaemonExpirationResult(GRACEFUL_EXPIRE, "after running out of JVM memory")
                : NOT_TRIGGERED;
    }

    static DaemonExpirationStrategy lowNonHeap() {
        return daemon -> daemon.getMemoryStatus().isNonHeapSpaceExhausted()
                ? new DaemonExpirationResult(GRACEFUL_EXPIRE, "after running out of JVM memory")
                : NOT_TRIGGERED;
    }

    static DaemonExpirationStrategy lowMemory(double minFreeMemoryPercentage) {
        if (Os.current() == Os.MAC) {
            return new OsxMemoryExpirationStrategy(minFreeMemoryPercentage);
        } else if (Os.current() == Os.LINUX) {
            return new MemInfoMemoryExpirationStrategy(minFreeMemoryPercentage);
        } else {
            return new MBeanMemoryExpirationStrategy(minFreeMemoryPercentage);
        }
    }

    static DaemonExpirationStrategy duplicateGracePeriod() {
        return idleTimeout(Environment.MVND_DUPLICATE_DAEMON_GRACE_PERIOD.asDuration());
    }

    static DaemonExpirationStrategy idleTimeout(Duration timeout) {
        return daemon -> {
            Duration idl = Duration.between(Instant.ofEpochMilli(daemon.getLastIdle()), Instant.now());
            if (daemon.getState() == DaemonState.Idle && idl.compareTo(timeout) > 0) {
                return new DaemonExpirationResult(QUIET_EXPIRE, "after being idle for " + TimeUtils.printDuration(idl));
            } else {
                return NOT_TRIGGERED;
            }
        };
    }

    static DaemonExpirationStrategy notMostRecentlyUsed() {
        return daemon -> daemon.getRegistry().getIdle().stream()
                        .max(Comparator.comparingLong(DaemonInfo::getLastBusy))
                        .map(d -> Objects.equals(d.getId(), daemon.getDaemonId()))
                        .orElse(false)
                ? new DaemonExpirationResult(GRACEFUL_EXPIRE, "not recently used")
                : NOT_TRIGGERED;
    }

    static DaemonExpirationStrategy registryUnavailable() {
        return daemon -> {
            try {
                if (!Files.isReadable(daemon.getRegistry().getRegistryFile())) {
                    return new DaemonExpirationResult(GRACEFUL_EXPIRE, "after the daemon registry became unreadable");
                } else if (daemon.getRegistry().get(daemon.getDaemonId()) == null) {
                    return new DaemonExpirationResult(
                            GRACEFUL_EXPIRE, "after the daemon was no longer found in the daemon registry");
                } else {
                    return NOT_TRIGGERED;
                }
            } catch (SecurityException e) {
                return new DaemonExpirationResult(GRACEFUL_EXPIRE, "after the daemon registry became inaccessible");
            }
        };
    }

    static DaemonExpirationStrategy compatible() {
        return daemon -> {
            DaemonCompatibilitySpec constraint = new DaemonCompatibilitySpec(
                    Paths.get(daemon.getInfo().getJavaHome()), daemon.getInfo().getOptions());
            long compatible = daemon.getRegistry().getAll().stream()
                    .map(constraint::isSatisfiedBy)
                    .filter(Result::isCompatible)
                    .count();
            if (compatible > 1) {
                return new DaemonExpirationResult(GRACEFUL_EXPIRE, "other compatible daemons were started");
            } else {
                return NOT_TRIGGERED;
            }
        };
    }

    /**
     * Expires the daemon only if all strategies would expire the daemon.
     */
    static DaemonExpirationStrategy all(DaemonExpirationStrategy... strategies) {
        return daemon -> {
            // If no expiration strategies exist, the daemon will not expire.
            DaemonExpirationResult expirationResult = NOT_TRIGGERED;
            DaemonExpirationStatus expirationStatus = DO_NOT_EXPIRE;

            List<String> reasons = new ArrayList<>();
            for (DaemonExpirationStrategy expirationStrategy : strategies) {
                // If any of the child strategies don't expire the daemon, the daemon will not expire.
                // Otherwise, the daemon will expire and aggregate the reasons together.
                expirationResult = expirationStrategy.checkExpiration(daemon);

                if (expirationResult.getStatus() == DO_NOT_EXPIRE) {
                    return NOT_TRIGGERED;
                } else {
                    reasons.add(expirationResult.getReason());
                    expirationStatus = highestPriorityOf(expirationResult.getStatus(), expirationStatus);
                }
            }

            if (expirationResult.getStatus() == DO_NOT_EXPIRE) {
                return NOT_TRIGGERED;
            } else {
                return new DaemonExpirationResult(expirationStatus, reason(reasons));
            }
        };
    }

    /**
     * Expires the daemon if any of the strategies would expire the daemon.
     */
    static DaemonExpirationStrategy any(DaemonExpirationStrategy... strategies) {
        return daemon -> {
            DaemonExpirationResult expirationResult;
            DaemonExpirationStatus expirationStatus = DO_NOT_EXPIRE;
            List<String> reasons = new ArrayList<>();

            for (DaemonExpirationStrategy expirationStrategy : strategies) {
                expirationResult = expirationStrategy.checkExpiration(daemon);
                if (expirationResult.getStatus() != DO_NOT_EXPIRE) {
                    reasons.add(expirationResult.getReason());
                    expirationStatus = highestPriorityOf(expirationResult.getStatus(), expirationStatus);
                }
            }

            if (expirationStatus == DO_NOT_EXPIRE) {
                return NOT_TRIGGERED;
            } else {
                return new DaemonExpirationResult(expirationStatus, reason(reasons));
            }
        };
    }

    private static String reason(List<String> reasons) {
        return reasons.stream().filter(Objects::nonNull).collect(Collectors.joining(" and "));
    }

    private static DaemonExpirationStatus highestPriorityOf(DaemonExpirationStatus left, DaemonExpirationStatus right) {
        if (left.ordinal() > right.ordinal()) {
            return left;
        } else {
            return right;
        }
    }

    public static class DaemonExpirationResult {

        public static final DaemonExpirationResult NOT_TRIGGERED = new DaemonExpirationResult(DO_NOT_EXPIRE, null);

        private final DaemonExpirationStatus status;
        private final String reason;

        public DaemonExpirationResult(DaemonExpirationStatus status, String reason) {
            this.status = status;
            this.reason = reason;
        }

        public DaemonExpirationStatus getStatus() {
            return status;
        }

        public String getReason() {
            return reason;
        }
    }

    private abstract static class MemoryExpirationStrategy implements DaemonExpirationStrategy {
        static final long MIN_THRESHOLD_BYTES = 384 * 1024 * 1024;
        static final long MAX_THRESHOLD_BYTES = 1024 * 1024 * 1024;

        final double minFreeMemoryPercentage;

        public MemoryExpirationStrategy(double minFreeMemoryPercentage) {
            this.minFreeMemoryPercentage = minFreeMemoryPercentage;
        }

        @Override
        public DaemonExpirationResult checkExpiration(Server daemon) {
            try {
                long[] mem = getTotalAndFreeMemory();
                if (mem != null && mem.length == 2) {
                    long total = mem[0];
                    long free = mem[1];
                    if (total > free && free > 0) {
                        double norm = Math.min(
                                Math.max(total * minFreeMemoryPercentage, MIN_THRESHOLD_BYTES), MAX_THRESHOLD_BYTES);
                        if (free < norm) {
                            return new DaemonExpirationResult(GRACEFUL_EXPIRE, "to reclaim system memory");
                        }
                    }
                }
            } catch (Exception e) {
                // ignore
            }
            return NOT_TRIGGERED;
        }

        protected abstract long[] getTotalAndFreeMemory() throws Exception;
    }

    private static class OsxMemoryExpirationStrategy extends MemoryExpirationStrategy {
        public OsxMemoryExpirationStrategy(double minFreeMemoryPercentage) {
            super(minFreeMemoryPercentage);
        }

        @Override
        protected long[] getTotalAndFreeMemory() throws Exception {
            long[] mem = new long[2];
            CLibrary.getOsxMemoryInfo(mem);
            return mem;
        }
    }

    private static class MemInfoMemoryExpirationStrategy extends MemoryExpirationStrategy {
        public MemInfoMemoryExpirationStrategy(double minFreeMemoryPercentage) {
            super(minFreeMemoryPercentage);
        }

        @Override
        protected long[] getTotalAndFreeMemory() throws Exception {
            Matcher m = Pattern.compile("^(\\S+):\\s+(\\d+) kB$").matcher("");
            long total = -1;
            long available = -1;
            long free = -1;
            long buffers = -1;
            long cached = -1;
            long reclaimable = -1;
            long mapped = -1;
            for (String line : Files.readAllLines(Paths.get("/proc/meminfo"))) {
                if (m.reset(line).matches()) {
                    String key = m.group(1);
                    long val = Long.parseLong(m.group(2)) * 1024;
                    switch (key) {
                        case "MemTotal":
                            total = val;
                            break;
                        case "MemAvailable":
                            available = val;
                            break;
                        case "MemFree":
                            free = val;
                            break;
                        case "Buffers":
                            buffers = val;
                            break;
                        case "Cached":
                            cached = val;
                            break;
                        case "SReclaimable":
                            reclaimable = val;
                            break;
                        case "Mapped":
                            mapped = val;
                            break;
                    }
                }
            }
            if (available < 0) {
                if (free != -1 && buffers != -1 && cached != -1 && reclaimable != -1 && mapped != -1) {
                    available = free + buffers + cached + reclaimable - mapped;
                }
            }
            return new long[] {total, available};
        }
    }

    private static class MBeanMemoryExpirationStrategy extends MemoryExpirationStrategy {
        final boolean isIbmJvm;

        public MBeanMemoryExpirationStrategy(double minFreeMemoryPercentage) {
            super(minFreeMemoryPercentage);
            String vendor = System.getProperty("java.vm.vendor");
            isIbmJvm = vendor.toLowerCase(Locale.ROOT).startsWith("ibm corporation");
        }

        @Override
        protected long[] getTotalAndFreeMemory() throws Exception {
            ObjectName objectName = new ObjectName("java.lang:type=OperatingSystem");
            List<Attribute> list = ManagementFactory.getPlatformMBeanServer()
                    .getAttributes(objectName, new String[] {
                        isIbmJvm ? "TotalPhysicalMemory" : "TotalPhysicalMemorySize", "FreePhysicalMemorySize"
                    })
                    .asList();
            long total = ((Number) list.get(0).getValue()).longValue();
            long free = ((Number) list.get(1).getValue()).longValue();
            return new long[] {total, free};
        }
    }
}
