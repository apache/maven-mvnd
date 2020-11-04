/*
 * Copyright 2016 the original author or authors.
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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import org.jboss.fuse.mvnd.common.DaemonCompatibilitySpec;
import org.jboss.fuse.mvnd.common.DaemonCompatibilitySpec.Result;
import org.jboss.fuse.mvnd.common.DaemonExpirationStatus;
import org.jboss.fuse.mvnd.common.DaemonInfo;
import org.jboss.fuse.mvnd.common.DaemonState;

import static org.jboss.fuse.mvnd.common.DaemonExpirationStatus.DO_NOT_EXPIRE;
import static org.jboss.fuse.mvnd.common.DaemonExpirationStatus.GRACEFUL_EXPIRE;
import static org.jboss.fuse.mvnd.common.DaemonExpirationStatus.IMMEDIATE_EXPIRE;
import static org.jboss.fuse.mvnd.common.DaemonExpirationStatus.QUIET_EXPIRE;
import static org.jboss.fuse.mvnd.daemon.DaemonExpiration.DaemonExpirationResult.NOT_TRIGGERED;

/**
 * File origin:
 * https://github.com/gradle/gradle/blob/v5.6.2/subprojects/launcher/src/main/java/org/gradle/launcher/daemon/server/MasterExpirationStrategy.java
 */
public class DaemonExpiration {

    public static final int DUPLICATE_DAEMON_GRACE_PERIOD_MS = 10000;

    public interface DaemonExpirationStrategy {

        DaemonExpirationResult checkExpiration(Server daemon);

    }

    public static DaemonExpirationStrategy master() {
        return any(
                any(gcTrashing(), lowHeapSpace(), lowNonHeap()),
                all(compatible(), duplicateGracePeriod(), notMostRecentlyUsed()),
                idleTimeout(Server::getIdleTimeout),
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
        // TODO
        return daemon -> NOT_TRIGGERED;
    }

    static DaemonExpirationStrategy duplicateGracePeriod() {
        return idleTimeout(daemon -> DUPLICATE_DAEMON_GRACE_PERIOD_MS);
    }

    private static final long HOUR = 60 * 60 * 1000;
    private static final long MINUTE = 60 * 1000;
    private static final long SECOND = 1000;

    static DaemonExpirationStrategy idleTimeout(ToLongFunction<Server> timeout) {
        return daemon -> {
            long len = timeout.applyAsLong(daemon);
            long idl = System.currentTimeMillis() - daemon.getLastIdle();
            if (daemon.getState() == DaemonState.Idle && idl > len) {
                String str;
                if (idl > HOUR) {
                    str = (idl / HOUR) + " hours";
                } else if (idl > MINUTE) {
                    str = (idl / MINUTE) + " minutes";
                } else {
                    str = (idl / SECOND) + " seconds";
                }
                return new DaemonExpirationResult(QUIET_EXPIRE, "after being idle for " + str);
            } else {
                return NOT_TRIGGERED;
            }
        };
    }

    static DaemonExpirationStrategy notMostRecentlyUsed() {
        return daemon -> daemon.getRegistry().getIdle().stream()
                .max(Comparator.comparingLong(DaemonInfo::getLastBusy))
                .map(d -> Objects.equals(d.getUid(), daemon.getUid()))
                .orElse(false)
                        ? new DaemonExpirationResult(GRACEFUL_EXPIRE, "not recently used")
                        : NOT_TRIGGERED;
    }

    static DaemonExpirationStrategy registryUnavailable() {
        return daemon -> {
            try {
                if (!Files.isReadable(daemon.getRegistry().getRegistryFile())) {
                    return new DaemonExpirationResult(GRACEFUL_EXPIRE, "after the daemon registry became unreadable");
                } else if (daemon.getRegistry().get(daemon.getUid()) == null) {
                    return new DaemonExpirationResult(GRACEFUL_EXPIRE,
                            "after the daemon was no longer found in the daemon registry");
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
}
