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

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DaemonMemoryStatus {

    static final int MAX_EVENTS = 20;

    final GcStrategy strategy;

    final GarbageCollectorMXBean garbageCollectorMXBean;
    final MemoryPoolMXBean heapMemoryPoolMXBean;
    final MemoryPoolMXBean nonHeapMemoryPoolMXBean;
    final Clock clock;
    final Deque<GcEvent> heapEvents = new ConcurrentLinkedDeque<>();
    final Deque<GcEvent> nonHeapEvents = new ConcurrentLinkedDeque<>();

    public enum GcStrategy {
        ORACLE_PARALLEL_CMS("PS Old Gen", "Metaspace", "PS MarkSweep", 1.2, 80, 80, 5.0),
        ORACLE_6_CMS("CMS Old Gen", "Metaspace", "ConcurrentMarkSweep", 1.2, 80, 80, 5.0),
        ORACLE_SERIAL("Tenured Gen", "Metaspace", "MarkSweepCompact", 1.2, 80, 80, 5.0),
        ORACLE_G1("G1 Old Gen", "Metaspace", "G1 Old Generation", 0.4, 75, 80, 2.0),
        IBM_ALL("Java heap", "Not Used", "MarkSweepCompact", 0.8, 70, -1, 6.0);

        final String garbageCollector;
        final String heapMemoryPool;
        final String nonHeapMemoryPool;
        final int heapUsageThreshold;
        final double heapRateThreshold;
        final int nonHeapUsageThreshold;
        final double thrashingThreshold;

        GcStrategy(
                String heapMemoryPool,
                String nonHeapMemoryPool,
                String garbageCollector,
                double heapRateThreshold,
                int heapUsageThreshold,
                int nonHeapUsageThreshold,
                double thrashingThreshold) {
            this.garbageCollector = garbageCollector;
            this.heapMemoryPool = heapMemoryPool;
            this.nonHeapMemoryPool = nonHeapMemoryPool;
            this.heapUsageThreshold = heapUsageThreshold;
            this.heapRateThreshold = heapRateThreshold;
            this.nonHeapUsageThreshold = nonHeapUsageThreshold;
            this.thrashingThreshold = thrashingThreshold;
        }
    }

    static class GcEvent {
        final Instant timestamp;
        final MemoryUsage usage;
        final long count;

        public GcEvent(Instant timestamp, MemoryUsage usage, long count) {
            this.timestamp = timestamp;
            this.usage = usage;
            this.count = count;
        }
    }

    static class GcStats {
        final double gcRate;
        final int usedPercent;

        public GcStats(double gcRate, int usedPercent) {
            this.gcRate = gcRate;
            this.usedPercent = usedPercent;
        }
    }

    public DaemonMemoryStatus(ScheduledExecutorService executor) {
        List<GarbageCollectorMXBean> garbageCollectors = ManagementFactory.getGarbageCollectorMXBeans();
        List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
        GcStrategy strategy = null;
        GarbageCollectorMXBean garbageCollector = null;
        MemoryPoolMXBean heapMemoryPoolMXBean = null;
        MemoryPoolMXBean nonHeapMemoryPoolMXBean = null;
        for (GcStrategy testStrategy : GcStrategy.values()) {
            garbageCollector = garbageCollectors.stream()
                    .filter(gc -> gc.getName().equals(testStrategy.garbageCollector))
                    .findFirst()
                    .orElse(null);
            heapMemoryPoolMXBean = memoryPoolMXBeans.stream()
                    .filter(mp -> mp.getName().equals(testStrategy.heapMemoryPool))
                    .findFirst()
                    .orElse(null);
            nonHeapMemoryPoolMXBean = memoryPoolMXBeans.stream()
                    .filter(mp -> mp.getName().equals(testStrategy.nonHeapMemoryPool))
                    .findFirst()
                    .orElse(null);
            if (garbageCollector != null && heapMemoryPoolMXBean != null && nonHeapMemoryPoolMXBean != null) {
                strategy = testStrategy;
                break;
            }
        }
        if (strategy != null) {
            this.strategy = strategy;
            this.garbageCollectorMXBean = garbageCollector;
            this.heapMemoryPoolMXBean = heapMemoryPoolMXBean;
            this.nonHeapMemoryPoolMXBean = nonHeapMemoryPoolMXBean;
            this.clock = Clock.systemUTC();
            executor.scheduleAtFixedRate(this::gatherData, 1, 1, TimeUnit.SECONDS);
        } else {
            this.strategy = null;
            this.garbageCollectorMXBean = null;
            this.heapMemoryPoolMXBean = null;
            this.nonHeapMemoryPoolMXBean = null;
            this.clock = null;
        }
    }

    protected void gatherData() {
        GcEvent latest = heapEvents.peekLast();
        long currentCount = garbageCollectorMXBean.getCollectionCount();
        // There has been a GC event
        if (latest == null || latest.count != currentCount) {
            slideAndInsert(
                    heapEvents, new GcEvent(clock.instant(), heapMemoryPoolMXBean.getCollectionUsage(), currentCount));
        }
        slideAndInsert(nonHeapEvents, new GcEvent(clock.instant(), nonHeapMemoryPoolMXBean.getUsage(), -1));
    }

    private void slideAndInsert(Deque<GcEvent> events, GcEvent event) {
        events.addLast(event);
        while (events.size() > MAX_EVENTS) {
            events.pollFirst();
        }
    }

    public boolean isTrashing() {
        if (strategy.heapUsageThreshold != 0 && strategy.thrashingThreshold != 0) {
            GcStats stats = heapStats();
            return stats != null
                    && stats.usedPercent >= strategy.heapUsageThreshold
                    && stats.gcRate >= strategy.thrashingThreshold;
        } else {
            return false;
        }
    }

    public boolean isHeapSpaceExhausted() {
        if (strategy.heapUsageThreshold != 0 && strategy.heapRateThreshold != 0) {
            GcStats stats = heapStats();
            return stats != null
                    && stats.usedPercent >= strategy.heapUsageThreshold
                    && stats.gcRate >= strategy.heapRateThreshold;
        } else {
            return false;
        }
    }

    public boolean isNonHeapSpaceExhausted() {
        if (strategy.nonHeapUsageThreshold != 0) {
            GcStats stats = nonHeapStats();
            return stats != null && stats.usedPercent >= strategy.nonHeapUsageThreshold;
        } else {
            return false;
        }
    }

    private GcStats heapStats() {
        if (heapEvents.size() >= 5) {
            // Maximum pool size is fixed, so we should only need to get it from the first event
            GcEvent first = heapEvents.iterator().next();
            long maxSizeInBytes = first.usage.getMax();
            if (maxSizeInBytes > 0) {
                double gcRate = gcRate(heapEvents);
                int usagePercent = (int) (averageUsage(heapEvents) * 100.0f / maxSizeInBytes);
                return new GcStats(gcRate, usagePercent);
            }
        }
        return null;
    }

    private GcStats nonHeapStats() {
        if (nonHeapEvents.size() >= 5) {
            // Maximum pool size is fixed, so we should only need to get it from the first event
            GcEvent first = heapEvents.iterator().next();
            long maxSizeInBytes = first.usage.getMax();
            if (maxSizeInBytes > 0) {
                int usagePercent = (int) (averageUsage(nonHeapEvents) * 100.0f / maxSizeInBytes);
                return new GcStats(0, usagePercent);
            }
        }
        return null;
    }

    private double gcRate(Deque<GcEvent> events) {
        GcEvent first = events.peekFirst();
        GcEvent last = events.peekLast();
        // Total number of garbage collection events observed in the window
        double gcCountDelta = last.count - first.count;
        // Time interval between the first event in the window and the last
        double timeDelta = Duration.between(first.timestamp, last.timestamp).toMillis();
        return gcCountDelta / timeDelta;
    }

    private double averageUsage(Collection<GcEvent> events) {
        return events.stream().mapToLong(e -> e.usage.getUsed()).average().getAsDouble();
    }
}
