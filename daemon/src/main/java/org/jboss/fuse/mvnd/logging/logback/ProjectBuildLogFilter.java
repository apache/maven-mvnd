/*
 * Copyright (c) 2015-2016 salesforce.com
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.fuse.mvnd.logging.logback;


import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import org.jboss.fuse.mvnd.logging.internal.SLF4J;

/**
 * Filters events that have Maven project build MDC values. This filter is useful to suppress
 * per-project console log messages, which are mangled and unreadable during multithreaded builds.
 * It can also be used to funnel per-project messages to remote log collector.
 * <p>
 * {@code onMatch} parameter takes {@code ACCEPT} or {@code DENY} values and controls whether the
 * filter will reject message with or without associated per-project MDC values.
 */
public class ProjectBuildLogFilter extends Filter<ILoggingEvent> {

    private FilterReply onMatch;
    private Level passthroughThreshold;

    public void setOnMatch(FilterReply onMatch) {
        this.onMatch = onMatch;
    }

    public void setPassthroughThreshold(Level threshold) {
        this.passthroughThreshold = threshold;
    }

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (passthroughThreshold != null && event.getLevel().isGreaterOrEqual(passthroughThreshold)) {
            return FilterReply.NEUTRAL;
        }

        Map<String, String> mdcMap = event.getMDCPropertyMap();
        if (mdcMap != null && mdcMap.containsKey(SLF4J.KEY_PROJECT_ID)) {
            return onMatch;
        }
        return FilterReply.NEUTRAL;
    }

    @Override
    public void start() {
        if (onMatch == null) {
            addError("onMatch action is not set, aborting");
            return;
        }
        super.start();
    }
}
