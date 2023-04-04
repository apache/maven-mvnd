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
package org.mvndaemon.mvnd.timing;

import javax.inject.Named;
import javax.inject.Singleton;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.eclipse.sisu.Typed;
import org.mvndaemon.mvnd.common.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Named("timing")
@Typed(EventSpy.class)
public class BuildTimeEventSpy extends AbstractEventSpy {

    public static final int MAX_NAME_LENGTH = 58;
    public static final String DIVIDER = "------------------------------------------------------------------------";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Session session;

    public BuildTimeEventSpy() {}

    private static String getExecutionProperty(final ExecutionEvent event, final String property, final String def) {
        MavenSession mavenSession = event.getSession();
        Properties systemProperties = mavenSession.getSystemProperties();
        Properties userProperties = mavenSession.getUserProperties();
        String output = userProperties.getProperty(property);
        output = output == null ? systemProperties.getProperty(property) : output;
        return output == null ? def : output;
    }

    @Override
    public void init(Context context) throws Exception {
        super.init(context);
    }

    @Override
    public void onEvent(Object event) throws Exception {
        if (event instanceof ExecutionEvent) {
            onEvent((ExecutionEvent) event);
        }
    }

    private void onEvent(ExecutionEvent event) throws Exception {
        switch (event.getType()) {
            case SessionStarted:
                logger.info("BuildTimeEventSpy is registered.");
                session = new Session();
                break;

            case MojoStarted:
                session.mojoTimer(event).start();
                break;

            case MojoFailed:
            case MojoSucceeded:
                session.mojoTimer(event).end();
                break;

            case SessionEnded:
                String prop = getExecutionProperty(
                        event, Environment.MVND_BUILD_TIME.getProperty(), Environment.MVND_BUILD_TIME.getDefault());
                boolean output = Boolean.parseBoolean(prop);
                doReport(output);
                break;

            default:
                // Ignore other events
        }
    }

    @Override
    public void close() throws Exception {
        super.close();
    }

    private void doReport(boolean output) {
        Consumer<String> log = output ? logger::info : logger::debug;
        log.accept(DIVIDER);
        log.accept("Build Time Summary:");
        log.accept(DIVIDER);
        Map<String, Long> mojos = new HashMap<>();
        session.projects().forEach(p -> {
            log.accept(String.format("%s [%.3fs]", p.name(), p.duration() / 1000d));
            p.mojos().forEach(m -> {
                log.accept(String.format("  %s [%.3fs]", m.name(), m.duration() / 1000d));
                mojos.merge(m.name(), m.duration(), Long::sum);
            });
        });
        log.accept(DIVIDER);
        mojos.forEach((name, duration) -> log.accept(String.format("  %s [%.3fs]", name, duration / 1000d)));
    }

    private static class Session {
        private final Map<String, Map<String, Timer>> session = new ConcurrentHashMap<>();

        public Stream<Project> projects() {
            return session.entrySet().stream().map(Project::new).sorted(Comparator.comparing(Project::startTime));
        }

        public Timer mojoTimer(ExecutionEvent event) {
            return session.computeIfAbsent(event.getProject().getArtifactId(), id -> new ConcurrentHashMap<>())
                    .computeIfAbsent(name(event.getMojoExecution()), mn -> new Timer());
        }

        private String name(MojoExecution mojoExecution) {
            return String.format(
                    Locale.ENGLISH,
                    "%s:%s (%s)",
                    mojoExecution.getArtifactId(),
                    mojoExecution.getGoal(),
                    mojoExecution.getExecutionId());
        }
    }

    private static class Project {
        private final Map.Entry<String, Map<String, Timer>> project;

        Project(Entry<String, Map<String, Timer>> project) {
            this.project = project;
        }

        public String name() {
            return project.getKey();
        }

        public long duration() {
            return endTime() - startTime();
        }

        public long startTime() {
            return project.getValue().values().stream()
                    .mapToLong(Timer::startTime)
                    .min()
                    .orElse(0);
        }

        public long endTime() {
            return project.getValue().values().stream()
                    .mapToLong(Timer::endTime)
                    .max()
                    .orElse(0);
        }

        public Stream<Mojo> mojos() {
            return project.getValue().entrySet().stream().map(Mojo::new).sorted(Comparator.comparing(Mojo::startTime));
        }
    }

    private static class Mojo {
        private final Map.Entry<String, Timer> mojo;

        Mojo(Map.Entry<String, Timer> mojo) {
            this.mojo = mojo;
        }

        public String name() {
            String name = mojo.getKey();
            if (name.length() < MAX_NAME_LENGTH) {
                StringBuilder sb = new StringBuilder(MAX_NAME_LENGTH);
                sb.append(name);
                sb.append(' ');
                while (sb.length() < MAX_NAME_LENGTH) {
                    sb.append('.');
                }
                return sb.toString();
            } else {
                return name.substring(0, MAX_NAME_LENGTH);
            }
        }

        public long duration() {
            return endTime() - startTime();
        }

        public long startTime() {
            return mojo.getValue().startTime();
        }

        public long endTime() {
            return mojo.getValue().endTime();
        }
    }

    private static class Timer {
        private long startTime = 0;
        private long endTime = 0;

        public long startTime() {
            return startTime;
        }

        public long endTime() {
            return endTime;
        }

        public void start() {
            startTime = System.currentTimeMillis();
        }

        public void end() {
            endTime = System.currentTimeMillis();
        }
    }
}
