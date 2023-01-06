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
package org.mvndaemon.mvnd.builder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.maven.execution.BuildFailure;
import org.apache.maven.execution.BuildSuccess;
import org.apache.maven.execution.BuildSummary;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.internal.LifecycleModuleBuilder;
import org.apache.maven.lifecycle.internal.ReactorContext;
import org.apache.maven.lifecycle.internal.TaskSegment;
import org.apache.maven.lifecycle.internal.builder.Builder;
import org.apache.maven.project.MavenProject;
import org.mvndaemon.mvnd.builder.ProjectExecutorService.ProjectRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maven {@link Builder} implementation that schedules execution of the reactor modules on the build
 * critical path first. Build critical path is estimated based on module build times collected
 * during a previous build, or based on module's downstream dependency trail length, if no prior
 * build time information is available.
 *
 * @author Brian Toal
 */
class SmartBuilderImpl {

    private final Logger logger = LoggerFactory.getLogger(SmartBuilder.class);

    // global components
    private final LifecycleModuleBuilder lifecycleModuleBuilder;

    // session-level components
    private final MavenSession rootSession;
    private final ReactorContext reactorContext;
    private final TaskSegment taskSegment;

    //
    private final ReactorBuildQueue reactorBuildQueue;
    private final ProjectExecutorService executor;
    private final int degreeOfConcurrency;

    //
    private final ReactorBuildStats stats;

    SmartBuilderImpl(
            LifecycleModuleBuilder lifecycleModuleBuilder,
            MavenSession session,
            ReactorContext reactorContext,
            TaskSegment taskSegment,
            Set<MavenProject> projects,
            DependencyGraph<MavenProject> graph) {
        this.lifecycleModuleBuilder = lifecycleModuleBuilder;
        this.rootSession = session;
        this.reactorContext = reactorContext;
        this.taskSegment = taskSegment;

        this.degreeOfConcurrency = session.getRequest().getDegreeOfConcurrency();

        final Comparator<MavenProject> projectComparator = ProjectComparator.create(graph);

        this.reactorBuildQueue = new ReactorBuildQueue(projects, graph);
        this.executor = new ProjectExecutorService(degreeOfConcurrency, projectComparator);

        this.stats = ReactorBuildStats.create(projects);
    }

    private static String projectGA(MavenProject project) {
        return project.getGroupId() + ":" + project.getArtifactId();
    }

    public ReactorBuildStats build() throws ExecutionException, InterruptedException {
        stats.recordStart();

        Set<MavenProject> rootProjects = reactorBuildQueue.getRootProjects();

        // this is the main build loop
        submitAll(rootProjects);
        long timstampSubmit = System.nanoTime();
        int submittedCount = rootProjects.size();
        while (submittedCount > 0) {
            Set<MavenProject> bottlenecks = null;
            if (submittedCount < degreeOfConcurrency) {
                bottlenecks = reactorBuildQueue.getReadyProjects();
            }

            try {
                MavenProject completedProject = executor.take();
                if (bottlenecks != null) {
                    stats.recordBottlenecks(bottlenecks, degreeOfConcurrency, System.nanoTime() - timstampSubmit);
                }
                logCompleted(completedProject);
                Set<MavenProject> readyProjects = reactorBuildQueue.onProjectFinish(completedProject);
                submitAll(readyProjects);
                timstampSubmit = System.nanoTime();
                submittedCount += (readyProjects.size() - 1);

                logBuildQueueStatus();
            } catch (ExecutionException e) {
                // we get here when unhandled exception or error occurred on the worker thread
                // this can be low-level system problem, like OOME, or runtime exception in maven code
                // there is no meaningful recovery, so we shutdown and rethrow the exception
                shutdown();
                throw e;
            }
        }
        shutdown();

        stats.recordStop();
        return stats;
    }

    private void logBuildQueueStatus() {
        int blockedCount = reactorBuildQueue.getBlockedCount();
        int finishedCount = reactorBuildQueue.getFinishedCount();
        int readyCount = reactorBuildQueue.getReadyCount();
        String runningProjects = "";
        if (readyCount < degreeOfConcurrency && blockedCount > 0) {
            runningProjects = reactorBuildQueue.getReadyProjects().stream()
                    .map(SmartBuilderImpl::projectGA)
                    .collect(Collectors.joining(" ", "[", "]"));
        }
        logger.debug(
                "Builder state: blocked={} finished={} ready-or-running={} {}",
                blockedCount,
                finishedCount,
                readyCount,
                runningProjects);
    }

    private void logCompleted(MavenProject project) {
        BuildSummary buildSummary = rootSession.getResult().getBuildSummary(project);
        String message = "SKIPPED";
        if (buildSummary instanceof BuildSuccess) {
            message = "SUCCESS";
        } else if (buildSummary instanceof BuildFailure) {
            message = "FAILURE";
        } else if (buildSummary != null) {
            logger.warn("Unexpected project build summary class {}", buildSummary.getClass());
            message = "UNKNOWN";
        }
        logger.debug("{} build of project {}:{}", message, project.getGroupId(), project.getArtifactId());
    }

    private void shutdown() {
        executor.shutdown();
    }

    public void cancel() {
        executor.cancel();
    }

    private void submitAll(Set<MavenProject> readyProjects) {
        List<ProjectBuildTask> tasks = new ArrayList<>();
        for (MavenProject project : readyProjects) {
            tasks.add(new ProjectBuildTask(project));
            logger.debug("Ready {}:{}", project.getGroupId(), project.getArtifactId());
        }
        executor.submitAll(tasks);
    }

    /* package */ void buildProject(MavenProject project) {
        logger.debug("STARTED build of project {}:{}", project.getGroupId(), project.getArtifactId());

        try {
            MavenSession copiedSession = rootSession.clone();
            lifecycleModuleBuilder.buildProject(copiedSession, rootSession, reactorContext, project, taskSegment);
        } catch (RuntimeException ex) {
            // preserve the xml stack trace, and the java cause chain
            rootSession.getResult().addException(new RuntimeException(project.getName() + ": " + ex.getMessage(), ex));
        }
    }

    class ProjectBuildTask implements ProjectRunnable {
        private final MavenProject project;

        ProjectBuildTask(MavenProject project) {
            this.project = project;
        }

        @Override
        public void run() {
            final long start = System.nanoTime();
            try {
                buildProject(project);
            } finally {
                stats.recordServiceTime(project, System.nanoTime() - start);
            }
        }

        @Override
        public MavenProject getProject() {
            return project;
        }
    }
}
