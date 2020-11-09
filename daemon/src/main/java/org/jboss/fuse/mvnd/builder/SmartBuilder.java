/*
 * Copyright 2014 the original author or authors.
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
package org.jboss.fuse.mvnd.builder;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.internal.LifecycleModuleBuilder;
import org.apache.maven.lifecycle.internal.ProjectBuildList;
import org.apache.maven.lifecycle.internal.ReactorBuildStatus;
import org.apache.maven.lifecycle.internal.ReactorContext;
import org.apache.maven.lifecycle.internal.TaskSegment;
import org.apache.maven.lifecycle.internal.builder.Builder;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Trivial Maven {@link Builder} implementation. All interesting stuff happens in
 * {@link SmartBuilderImpl} .
 *
 * File origin:
 * https://github.com/takari/takari-smart-builder/blob/takari-smart-builder-0.6.1/src/main/java/io/takari/maven/builder/smart/SmartBuilder.java
 */
@Singleton
@Named("smart")
@Default
public class SmartBuilder implements Builder {

    public static final String PROP_PROFILING = "smartbuilder.profiling";
    public static final String MVND_BUILDER_RULES = "mvnd.builder.rules";
    public static final String MVND_BUILDER_RULE = "mvnd.builder.rule";
    public static final String MVND_BUILDER_RULES_PROVIDER_URL = "mvnd.builder.rules.provider.url";
    public static final String MVND_BUILDER_RULES_PROVIDER_SCRIPT = "mvnd.builder.rules.provider.script";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    static final Pattern mvndRuleSanitizerPattern = Pattern.compile("[,\\s]+");

    private final LifecycleModuleBuilder moduleBuilder;

    private volatile SmartBuilderImpl builder;
    private volatile boolean canceled;

    private static SmartBuilder INSTANCE;

    public static SmartBuilder cancel() {
        SmartBuilder builder = INSTANCE;
        if (builder != null) {
            builder.doCancel();
        }
        return builder;
    }

    @Inject
    public SmartBuilder(LifecycleModuleBuilder moduleBuilder) {
        this.moduleBuilder = moduleBuilder;
        INSTANCE = this;
    }

    void doCancel() {
        canceled = true;
        SmartBuilderImpl b = builder;
        if (b != null) {
            b.cancel();
        }
    }

    public void doneCancel() {
        canceled = false;
    }

    @Override
    public synchronized void build(final MavenSession session, final ReactorContext reactorContext,
            ProjectBuildList projectBuilds, final List<TaskSegment> taskSegments,
            ReactorBuildStatus reactorBuildStatus) throws ExecutionException, InterruptedException {
        List<String> list = new ArrayList<>();

        String providerScript = null;
        String providerUrl = session.getTopLevelProject().getProperties()
                .getProperty(MVND_BUILDER_RULES_PROVIDER_URL);
        if (providerUrl != null) {
            URL url;
            try {
                url = new URL(providerUrl);
            } catch (MalformedURLException e) {
                try {
                    url = new File(providerUrl).toURI().toURL();
                } catch (MalformedURLException ex) {
                    url = null;
                }
            }
            if (url == null) {
                throw new ExecutionException("Bad syntax for " + MVND_BUILDER_RULES_PROVIDER_URL, null);
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream()))) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                int l;
                while ((l = r.read(buf)) >= 0) {
                    sb.append(buf, 0, l);
                }
                providerScript = sb.toString();
            } catch (IOException e) {
                throw new ExecutionException("Unable to read provider url " + MVND_BUILDER_RULES_PROVIDER_URL, e);
            }
        }
        if (providerScript == null) {
            providerScript = session.getTopLevelProject().getProperties()
                    .getProperty(MVND_BUILDER_RULES_PROVIDER_SCRIPT);
        }
        if (providerScript != null) {
            Binding binding = new Binding();
            GroovyShell shell = new GroovyShell(binding);
            binding.setProperty("session", session);
            Object result = shell.evaluate(providerScript);
            if (result instanceof Iterable) {
                for (Object r : (Iterable) result) {
                    list.add(r.toString());
                }
            } else if (result != null) {
                list.add(result.toString());
            } else {
                throw new ExecutionException("The provider script did not return a valid string or string collection", null);
            }
            list.add(result.toString());
        }

        String topRule = session.getTopLevelProject().getProperties()
                .getProperty(MVND_BUILDER_RULES);
        if (topRule != null) {
            list.add(topRule);
        }

        session.getAllProjects().forEach(p -> {
            String rule = p.getProperties().getProperty(MVND_BUILDER_RULE);
            if (rule != null) {
                rule = rule.trim();
                if (!rule.isEmpty()) {
                    rule = mvndRuleSanitizerPattern.matcher(rule).replaceAll(",");
                    list.add(rule + " before " + p.getGroupId() + ":" + p.getArtifactId());
                }
            }
        });
        String rules = null;
        if (!list.isEmpty()) {
            rules = String.join("\n", list);
        }

        DependencyGraph<MavenProject> graph = DependencyGraph.fromMaven(session.getProjectDependencyGraph(), rules);

        // log overall build info
        final int degreeOfConcurrency = session.getRequest().getDegreeOfConcurrency();
        logger.info("Task segments : " + taskSegments.stream().map(Object::toString).collect(Collectors.joining(" ")));
        logger.info("Build maximum degree of concurrency is " + degreeOfConcurrency);
        logger.info("Total number of projects is " + graph.getProjects().count());

        // the actual build execution
        List<Map.Entry<TaskSegment, ReactorBuildStats>> allstats = new ArrayList<>();
        for (TaskSegment taskSegment : taskSegments) {
            Set<MavenProject> projects = projectBuilds.getByTaskSegment(taskSegment).getProjects();
            if (canceled) {
                return;
            }
            builder = new SmartBuilderImpl(moduleBuilder, session, reactorContext, taskSegment, projects, graph);
            try {
                ReactorBuildStats stats = builder.build();
                allstats.add(new AbstractMap.SimpleEntry<>(taskSegment, stats));
            } finally {
                builder = null;
            }
        }

        if (session.getResult().hasExceptions()) {
            // don't report stats of failed builds
            return;
        }

        // log stats of each task segment
        for (Map.Entry<TaskSegment, ReactorBuildStats> entry : allstats) {
            TaskSegment taskSegment = entry.getKey();
            ReactorBuildStats stats = entry.getValue();
            Set<MavenProject> projects = projectBuilds.getByTaskSegment(taskSegment).getProjects();

            logger.debug("Task segment {}, number of projects {}", taskSegment, projects.size());

            final long walltimeReactor = stats.walltimeTime(TimeUnit.NANOSECONDS);
            final long walltimeService = stats.totalServiceTime(TimeUnit.NANOSECONDS);
            final String effectiveConcurrency = String.format("%2.2f", ((double) walltimeService) / walltimeReactor);
            logger.info(
                    "Segment walltime {} s, segment projects service time {} s, effective/maximum degree of concurrency {}/{}",
                    TimeUnit.NANOSECONDS.toSeconds(walltimeReactor),
                    TimeUnit.NANOSECONDS.toSeconds(walltimeService), effectiveConcurrency,
                    degreeOfConcurrency);

            if (projects.size() > 1 && isProfiling(session)) {
                logger.info(stats.renderCriticalPath(graph));
            }
        }
    }

    private boolean isProfiling(MavenSession session) {
        String value = session.getUserProperties().getProperty(PROP_PROFILING);
        if (value == null) {
            value = session.getSystemProperties().getProperty(PROP_PROFILING);
        }
        return Boolean.parseBoolean(value);
    }

}
