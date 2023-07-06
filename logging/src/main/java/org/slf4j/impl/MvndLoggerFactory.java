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
package org.slf4j.impl;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.maven.logwrapper.LogLevelRecorder;
import org.apache.maven.logwrapper.MavenSlf4jWrapperFactory;
import org.slf4j.Logger;

/**
 * LogFactory for Maven which can create a simple logger or one which, if set, fails the build on a severity threshold.
 */
public class MvndLoggerFactory implements MavenSlf4jWrapperFactory {
    private LogLevelRecorder logLevelRecorder = null;
    private final ConcurrentMap<String, Logger> loggerMap = new ConcurrentHashMap<>();

    public MvndLoggerFactory() {
        MvndSimpleLogger.lazyInit();
    }

    @Override
    public void setLogLevelRecorder(LogLevelRecorder logLevelRecorder) {
        if (this.logLevelRecorder != null) {
            throw new IllegalStateException("LogLevelRecorder has already been set.");
        }

        this.logLevelRecorder = logLevelRecorder;
    }

    @Override
    public Optional<LogLevelRecorder> getLogLevelRecorder() {
        return Optional.ofNullable(logLevelRecorder);
    }

    /**
     * Return an appropriate {@link Logger} instance by name.
     */
    @Override
    public Logger getLogger(String name) {
        return loggerMap.computeIfAbsent(name, this::getNewLoggingInstance);
    }

    private Logger getNewLoggingInstance(String name) {
        if (name.startsWith("org.mvndaemon.mvnd.daemon")) {
            return new MvndDaemonLogger(name);
        } else if (logLevelRecorder == null) {
            return new MvndSimpleLogger(name);
        } else {
            return new MvndFailOnSeverityLogger(name, logLevelRecorder);
        }
    }

    public void reconfigure() {
        SimpleLoggerConfiguration config = MvndSimpleLogger.CONFIG_PARAMS;
        config.init();
        loggerMap.values().forEach(l -> {
            if (l instanceof MvndSimpleLogger) {
                ((MvndSimpleLogger) l).configure(config.defaultLogLevel);
            }
        });
    }
}
