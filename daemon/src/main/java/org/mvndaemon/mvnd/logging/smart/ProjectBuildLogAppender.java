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
package org.mvndaemon.mvnd.logging.smart;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Context;
import java.util.Map;
import org.apache.maven.shared.utils.logging.LoggerLevelRenderer;
import org.apache.maven.shared.utils.logging.MessageUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Forwards log messages to the client.
 */
public class ProjectBuildLogAppender extends AppenderBase<ILoggingEvent> implements AutoCloseable {

    private static final String KEY_PROJECT_ID = "maven.project.id";
    private static final ThreadLocal<String> PROJECT_ID = new InheritableThreadLocal<>();

    public static String getProjectId() {
        return PROJECT_ID.get();
    }

    public static void setProjectId(String projectId) {
        if (projectId != null) {
            PROJECT_ID.set(projectId);
            MDC.put(KEY_PROJECT_ID, projectId);
        } else {
            PROJECT_ID.remove();
            MDC.remove(KEY_PROJECT_ID);
        }
    }

    public static void updateMdc() {
        String id = getProjectId();
        if (id != null) {
            MDC.put(KEY_PROJECT_ID, id);
        } else {
            MDC.remove(KEY_PROJECT_ID);
        }
    }

    private static final String pattern = "[%level] %msg%n";
    private final PatternLayout layout;
    private final AbstractLoggingSpy loggingSpy;

    public ProjectBuildLogAppender(AbstractLoggingSpy loggingSpy) {
        this.loggingSpy = loggingSpy;
        this.name = ProjectBuildLogAppender.class.getName();
        this.context = (Context) LoggerFactory.getILoggerFactory();

        final PatternLayout l = new PatternLayout();
        l.setContext(context);
        l.setPattern(pattern);
        final Map<String, String> instanceConverterMap = l.getInstanceConverterMap();
        final String levelConverterClassName = LevelConverter.class.getName();
        instanceConverterMap.put("level", levelConverterClassName);
        instanceConverterMap.put("le", levelConverterClassName);
        instanceConverterMap.put("p", levelConverterClassName);
        this.layout = l;

        final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(this);

        start();
    }

    @Override
    public void start() {
        layout.start();
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        Map<String, String> mdc = event.getMDCPropertyMap();
        String projectId = mdc != null ? mdc.get(KEY_PROJECT_ID) : null;
        loggingSpy.projectLogMessage(projectId, layout.doLayout(event));
    }

    public static class LevelConverter extends ClassicConverter {
        @Override
        public String convert(ILoggingEvent event) {
            LoggerLevelRenderer llr = MessageUtils.level();
            Level level = event.getLevel();
            switch (level.toInt()) {
            case Level.ERROR_INT:
                return llr.error(level.toString());
            case Level.WARN_INT:
                return llr.warning(level.toString());
            case Level.INFO_INT:
                return llr.info(level.toString());
            default:
                return llr.debug(level.toString());
            }
        }
    }

    @Override
    public void close() {
        stop();
        final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.detachAppender(this);
    }

    @Override
    public void stop() {
        layout.stop();
        super.stop();
    }
}
