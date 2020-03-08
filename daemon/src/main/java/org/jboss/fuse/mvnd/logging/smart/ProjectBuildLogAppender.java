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
package org.jboss.fuse.mvnd.logging.smart;


import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.apache.maven.shared.utils.logging.LoggerLevelRenderer;
import org.apache.maven.shared.utils.logging.MessageUtils;

/**
 * This Maven-specific appender outputs project build log messages
 * to the smart logging system.
 */
public class ProjectBuildLogAppender extends AppenderBase<ILoggingEvent> {
    public static final String KEY_PROJECT_ID = "maven.project.id";

    private String pattern;
    private PatternLayout layout;

    @Override
    public void start() {
        if (pattern == null) {
            addError("\"Pattern\" property not set for appender named [" + name + "].");
            return;
        }
        layout = new PatternLayout();
        layout.setContext(context);
        layout.setPattern(pattern);
        layout.getInstanceConverterMap().put("level", LevelConverter.class.getName());
        layout.getInstanceConverterMap().put("le", LevelConverter.class.getName());
        layout.getInstanceConverterMap().put("p", LevelConverter.class.getName());
        layout.start();
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        Map<String, String> mdc = event.getMDCPropertyMap();
        String projectId = mdc != null ? mdc.get(KEY_PROJECT_ID) : null;
        AbstractLoggingSpy.instance().append(projectId, layout.doLayout(event));
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
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
}
