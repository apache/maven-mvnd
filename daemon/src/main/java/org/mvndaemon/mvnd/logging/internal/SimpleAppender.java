/*
 * Copyright 2020 the original author or authors.
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
package org.mvndaemon.mvnd.logging.internal;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.CoreConstants;

import static org.apache.maven.shared.utils.logging.MessageUtils.level;

/**
 * This appender acts like the slf4j simple logger.
 * It's used
 */
public class SimpleAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent eventObject) {
        StringBuilder buf = new StringBuilder();
        buf.append('[');
        buf.append(renderLevel(eventObject.getLevel()));
        buf.append(']');
        buf.append(' ');
        buf.append(eventObject.getFormattedMessage());
        buf.append(CoreConstants.LINE_SEPARATOR);
        IThrowableProxy tp = eventObject.getThrowableProxy();
        if (tp != null) {
            buf.append(CoreConstants.LINE_SEPARATOR);
            buf.append(new ThrowableProxyConverter().convert(eventObject));
        }
        System.out.print(buf.toString());
    }

    private String renderLevel(Level level) {
        switch (level.toInt()) {
        case Level.TRACE_INT:
            return level().debug("TRACE");
        case Level.DEBUG_INT:
            return level().debug("DEBUG");
        case Level.INFO_INT:
            return level().info("INFO");
        case Level.WARN_INT:
            return level().warning("WARNING");
        case Level.ERROR_INT:
            return level().error("ERROR");
        default:
            throw new IllegalStateException("Level " + level + " is unknown.");
        }

    }

}
