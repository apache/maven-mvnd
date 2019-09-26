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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.jline.utils.AttributedString;

public abstract class AbstractLoggingSpy extends AbstractEventSpy {

    private static AbstractLoggingSpy instance;

    public static AbstractLoggingSpy instance() {
        if (instance == null) {
            instance = new MavenLoggingSpy();
        }
        return instance;
    }

    public static void instance(AbstractLoggingSpy instance) {
        AbstractLoggingSpy.instance = instance;
    }

    protected Map<String, ProjectBuild> projects;
    protected List<String> events;

    @Override
    public void init(Context context) throws Exception {
        projects = new LinkedHashMap<>();
        events = new ArrayList<>();
    }

    @Override
    public void close() throws Exception {
        events = null;
        projects = null;
    }

    @Override
    public void onEvent(Object event) throws Exception {
        if (event instanceof ExecutionEvent) {
            ExecutionEvent executionEvent = (ExecutionEvent) event;
            switch (executionEvent.getType()) {
                case SessionStarted:
                    notifySessionStart(executionEvent);
                    break;
                case SessionEnded:
                    notifySessionFinish(executionEvent);
                    break;
                case ProjectStarted:
                    notifyProjectBuildStart(executionEvent);
                    break;
                case ProjectSucceeded:
                case ProjectFailed:
                case ProjectSkipped:
                    notifyProjectBuildFinish(executionEvent);
                    break;
                case MojoStarted:
                    notifyMojoExecutionStart(executionEvent);
                    break;
                case MojoSucceeded:
                case MojoSkipped:
                case MojoFailed:
                    notifyMojoExecutionFinish(executionEvent);
                    break;
                default:
                    break;
            }
        }
    }

    protected void notifySessionStart(ExecutionEvent event) {
    }

    protected void notifySessionFinish(ExecutionEvent event) {
    }

    protected synchronized void notifyProjectBuildStart(ExecutionEvent event) {
        ProjectBuild pb = new ProjectBuild();
        pb.project = event.getProject();
        pb.execution = event.getMojoExecution();
        pb.events = new ArrayList<>();
        projects.putIfAbsent(event.getProject().getId(), pb);
        onStartProject(pb);
    }

    protected void onStartProject(ProjectBuild project) {
        update();
    }

    protected synchronized void notifyProjectBuildFinish(ExecutionEvent event) throws Exception {
        ProjectBuild pb = projects.remove(event.getProject().getId());
        if (pb != null) {
            events.addAll(pb.events);
            onStopProject(pb);
        }
    }

    protected void onStopProject(ProjectBuild project) {
        update();
    }

    protected synchronized void notifyMojoExecutionStart(ExecutionEvent event) {
        ProjectBuild pb = projects.get(event.getProject().getId());
        if (pb != null) {
            pb.execution = event.getMojoExecution();
            onStartMojo(pb);
        }
    }

    protected void onStartMojo(ProjectBuild project) {
        update();
    }

    protected synchronized void notifyMojoExecutionFinish(ExecutionEvent event) {
        ProjectBuild pb = projects.get(event.getProject().getId());
        if (pb != null) {
            pb.execution = null;
            onStopMojo(pb);
        }
    }

    protected void onStopMojo(ProjectBuild project) {
        update();
    }

    protected void update() {
    }

    public void append(String projectId, String event) {
        ProjectBuild project = projectId != null ? projects.get(projectId) : null;
        if (project != null) {
            project.events.add(event);
        } else {
            events.add(event);
        }
    }

    protected static class ProjectBuild {
        MavenProject project;
        volatile MojoExecution execution;
        List<String> events;

        @Override
        public String toString() {
            MojoExecution e = execution;
            return e != null ? ":" + project.getArtifactId() + ":" + e.toString() : ":" + project.getArtifactId();
        }

        public AttributedString toDisplay() {
            return new AttributedString(toString());
        }

        public String projectId() {
            return project.getArtifactId();
        }
    }

}
