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

import java.util.List;
import java.util.Map;
import org.apache.maven.cli.logging.Slf4jLogger;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.jboss.fuse.mvnd.common.Message;
import org.jline.utils.AttributedString;
import org.slf4j.MDC;

import static org.jboss.fuse.mvnd.logging.smart.ProjectBuildLogAppender.KEY_PROJECT_ID;

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
    protected List<Message.BuildMessage> events;

    @Override
    public synchronized void init(Context context) throws Exception {
    }

    @Override
    public synchronized void close() throws Exception {
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

    protected void notifyProjectBuildStart(ExecutionEvent event) {
        onStartProject(getProjectId(event), getProjectDisplay(event));
    }

    protected void notifyProjectBuildFinish(ExecutionEvent event) throws Exception {
        onStopProject(getProjectId(event), getProjectDisplay(event));
    }

    protected void notifyMojoExecutionStart(ExecutionEvent event) {
        onStartMojo(getProjectId(event), getProjectDisplay(event));
    }

    protected void notifyMojoExecutionFinish(ExecutionEvent event) {
        onStopMojo(getProjectId(event), getProjectDisplay(event));
    }

    protected void onStartProject(String projectId, String display) {
        MDC.put(KEY_PROJECT_ID, projectId);
        update();
    }

    protected void onStopProject(String projectId, String display) {
        MDC.put(KEY_PROJECT_ID, projectId);
        update();
        MDC.remove(KEY_PROJECT_ID);
    }

    protected void onStartMojo(String projectId, String display) {
        Slf4jLogger.setCurrentProject(projectId);
        MDC.put(KEY_PROJECT_ID, projectId);
        update();
    }

    protected void onStopMojo(String projectId, String display) {
        MDC.put(KEY_PROJECT_ID, projectId);
        update();
    }

    protected void onProjectLog(String projectId, String message) {
        MDC.put(KEY_PROJECT_ID, projectId);
        update();
    }

    protected void update() {
    }

    private String getProjectId(ExecutionEvent event) {
        return event.getProject().getArtifactId();
    }

    private String getProjectDisplay(ExecutionEvent event) {
        String projectId = getProjectId(event);
        String disp = event.getMojoExecution() != null
                ? ":" + projectId + ":" + event.getMojoExecution().toString()
                : ":" + projectId;
        return disp;
    }

    public void append(String projectId, String event) {
        String msg = event.endsWith("\n") ? event.substring(0, event.length() - 1) : event;
        onProjectLog(projectId, msg);
    }

    protected static class ProjectBuild {
        MavenProject project;
        volatile MojoExecution execution;

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
