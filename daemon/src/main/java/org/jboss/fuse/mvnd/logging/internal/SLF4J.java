/*
 * Copyright (c) 2015-2016 salesforce.com
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.fuse.mvnd.logging.internal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.Lifecycle;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.slf4j.MDC;

/**
 * File origin: https://github.com/takari/concurrent-build-logger/blob/concurrent-build-logger-0.1.0/src/main/java/io/takari/maven/logging/internal/SLF4J.java
 */
public final class SLF4J {
    public static final String KEY_PROJECT_ID = "maven.project.id";
    public static final String KEY_PROJECT_GROUPID = "maven.project.groupId";
    public static final String KEY_PROJECT_ARTIFACTID = "maven.project.artifactId";
    public static final String KEY_PROJECT_VERSION = "maven.project.version";
    public static final String KEY_PROJECT_BASEDIR = "maven.project.basedir";
    public static final String KEY_PROJECT_LOGDIR = "maven.project.logdir";
    public static final String KEY_MOJO_ID = "maven.mojo.id";
    public static final String KEY_MOJO_GROUPID = "maven.mojo.groupId";
    public static final String KEY_MOJO_ARTIFACTID = "maven.mojo.artifactId";
    public static final String KEY_MOJO_VERSION = "maven.mojo.version";
    public static final String KEY_MOJO_GOAL = "maven.mojo.goal";
    private static List<LifecycleListener> listeners = new CopyOnWriteArrayList<>();

    private SLF4J() {
    }

    /**
     * Returns conventional per-project build log directory.
     */
    public static String getLogdir(MavenProject project) {
        return project.getBuild().getDirectory();
    }

    /**
     * Puts specified project to the current thread's diagnostic context.
     *
     * @see org.apache.maven.lifecycle.internal.builder.Builder
     */
    public static void putMDC(MavenProject project) {
        if (project == null || project.getBasedir() == null || !project.getBasedir().exists()) {
            // ignore standalone mvn execution
            return;
        }
        MDC.put(KEY_PROJECT_ID, project.getId());
        MDC.put(KEY_PROJECT_GROUPID, project.getGroupId());
        MDC.put(KEY_PROJECT_ARTIFACTID, project.getArtifactId());
        MDC.put(KEY_PROJECT_BASEDIR, project.getBasedir().getAbsolutePath());
        MDC.put(KEY_PROJECT_LOGDIR, getLogdir(project));
    }

    /**
     * Removes project information from the current thread's diagnostic context.
     *
     * @see org.apache.maven.lifecycle.internal.builder.Builder
     */
    public static void removeMDC(MavenProject project) {
        if (project == null) {
            return;
        }
        MDC.remove(KEY_PROJECT_ID);
        MDC.remove(KEY_PROJECT_GROUPID);
        MDC.remove(KEY_PROJECT_ARTIFACTID);
        MDC.remove(KEY_PROJECT_BASEDIR);
        MDC.remove(KEY_PROJECT_LOGDIR);
    }

    public static void addListener(LifecycleListener listener) {
        listeners.add(listener);
    }

    public static void removeListener(LifecycleListener listener) {
        listeners.removeIf(lifecycleListener -> lifecycleListener == listener);
    }

    static void notifySessionStart(MavenSession session) {
        listeners.forEach(listener -> listener.onSessionStart(session));
    }

    static void notifySessionFinish(MavenSession session) {
        listeners.forEach(listener -> listener.onSessionFinish(session));
    }

    static void notifyProjectBuildStart(MavenProject project) {
        putMDC(project);
        listeners.forEach(listener -> listener.onProjectBuildStart(project));
    }

    static void notifyProjectBuildFinish(MavenProject project) {
        listeners.forEach(listener -> listener.onProjectBuildFinish(project));
        removeMDC(project);
    }

    static void notifyMojoExecutionStart(MavenProject project, Lifecycle lifecycle,
                                         MojoExecution execution) {
        StringBuilder id = new StringBuilder();
        id.append(execution.getGroupId());
        id.append(':');
        id.append(execution.getArtifactId());
        id.append(':');
        id.append(execution.getGoal());
        if (!execution.getExecutionId().equals("default-" + execution.getGoal())) {
            id.append(':');
            id.append(execution.getExecutionId());
        }
        MDC.put(KEY_MOJO_ID, id.toString());
        MDC.put(KEY_MOJO_GROUPID, execution.getGroupId());
        MDC.put(KEY_MOJO_ARTIFACTID, execution.getArtifactId());
        MDC.put(KEY_MOJO_VERSION, execution.getVersion());
        MDC.put(KEY_MOJO_GOAL, execution.getGoal());
        for (LifecycleListener listener : listeners) {
            listener.onMojoExecutionStart(project, lifecycle, execution);
        }
    }

    static void notifyMojoExecutionFinish(MavenProject project, MojoExecution execution) {
        MDC.remove(KEY_MOJO_ID);
        MDC.remove(KEY_MOJO_GROUPID);
        MDC.remove(KEY_MOJO_ARTIFACTID);
        MDC.remove(KEY_MOJO_VERSION);
        MDC.remove(KEY_MOJO_GOAL);
    }

    public interface LifecycleListener {
        default void onSessionStart(MavenSession session) {
        }

        default void onSessionFinish(MavenSession session) {
        }

        default void onProjectBuildStart(MavenProject project) {
        }

        default void onProjectBuildFinish(MavenProject project) {
        }

        default void onMojoExecutionStart(MavenProject project, Lifecycle lifecycle,
                                          MojoExecution execution) {
        }
    }
}
