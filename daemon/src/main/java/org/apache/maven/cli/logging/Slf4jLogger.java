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
package org.apache.maven.cli.logging;

import org.codehaus.plexus.logging.Logger;
import org.jboss.fuse.mvnd.logging.smart.ProjectBuildLogAppender;
import org.slf4j.MDC;

/**
 * Adapt an SLF4J logger to a Plexus logger, ignoring Plexus logger API parts that are not classical and
 * probably not really used.
 *
 * <p>
 * Adapted from
 * https://github.com/apache/maven/blob/maven-3.6.3/maven-embedder/src/main/java/org/apache/maven/cli/logging/Slf4jLogger.java
 *
 * @author Jason van Zyl
 */
public class Slf4jLogger
        implements Logger {

    private static final ThreadLocal<String> PROJECT_ID = new ThreadLocal<>();

    private org.slf4j.Logger logger;
    private String projectId;

    public Slf4jLogger(org.slf4j.Logger logger) {
        this.logger = logger;
        this.projectId = PROJECT_ID.get();
    }

    public static void setCurrentProject(String projectId) {
        PROJECT_ID.set(projectId);
    }

    public void debug(String message) {
        setMdc();
        logger.debug(message);
    }

    public void debug(String message, Throwable throwable) {
        setMdc();
        logger.debug(message, throwable);
    }

    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    public void info(String message) {
        setMdc();
        logger.info(message);
    }

    public void info(String message, Throwable throwable) {
        setMdc();
        logger.info(message, throwable);
    }

    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    public void warn(String message) {
        setMdc();
        logger.warn(message);
    }

    public void warn(String message, Throwable throwable) {
        setMdc();
        logger.warn(message, throwable);
    }

    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    public void error(String message) {
        setMdc();
        logger.error(message);
    }

    public void error(String message, Throwable throwable) {
        setMdc();
        logger.error(message, throwable);
    }

    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    public void fatalError(String message) {
        setMdc();
        logger.error(message);
    }

    public void fatalError(String message, Throwable throwable) {
        setMdc();
        logger.error(message, throwable);
    }

    public boolean isFatalErrorEnabled() {
        return logger.isErrorEnabled();
    }

    /**
     * <b>Warning</b>: ignored (always return <code>0 == Logger.LEVEL_DEBUG</code>).
     */
    public int getThreshold() {
        return 0;
    }

    /**
     * <b>Warning</b>: ignored.
     */
    public void setThreshold(int threshold) {
    }

    /**
     * <b>Warning</b>: ignored (always return <code>null</code>).
     */
    public Logger getChildLogger(String name) {
        return null;
    }

    public String getName() {
        return logger.getName();
    }

    private void setMdc() {
        if (projectId != null) {
            MDC.put(ProjectBuildLogAppender.KEY_PROJECT_ID, projectId);
        }
    }

}
