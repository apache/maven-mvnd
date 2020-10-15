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

import org.apache.maven.execution.ExecutionEvent;

public abstract class AbstractLoggingSpy {

    private static AbstractLoggingSpy instance;

    public static AbstractLoggingSpy instance() {
        if (instance == null) {
            if ("mvns".equals(System.getProperty("mvnd.logging", "mvn"))) {
                instance = new MavenLoggingSpy();
            } else {
                instance = new AbstractLoggingSpy() {
                };
            }
        }
        return instance;
    }

    public static void instance(AbstractLoggingSpy instance) {
        AbstractLoggingSpy.instance = instance;
    }

    public void append(String projectId, String event) {
        String msg = event.endsWith("\n") ? event.substring(0, event.length() - 1) : event;
        onProjectLog(projectId, msg);
    }

    protected void notifySessionStart(ExecutionEvent event) {
        onStartSession();
    }

    protected void notifySessionFinish(ExecutionEvent event) {
        onFinishSession();
    }

    protected void notifyProjectBuildStart(ExecutionEvent event) {
        onStartProject(getProjectId(event), getProjectDisplay(event));
    }

    protected void notifyProjectBuildFinish(ExecutionEvent event) {
        onStopProject(getProjectId(event), getProjectDisplay(event));
    }

    protected void notifyMojoExecutionStart(ExecutionEvent event) {
        onStartMojo(getProjectId(event), getProjectDisplay(event));
    }

    protected void notifyMojoExecutionFinish(ExecutionEvent event) {
        onStopMojo(getProjectId(event), getProjectDisplay(event));
    }

    protected void onStartSession() {
    }

    protected void onFinishSession() {
    }

    protected void onStartProject(String projectId, String display) {
    }

    protected void onStopProject(String projectId, String display) {
    }

    protected void onStartMojo(String projectId, String display) {
    }

    protected void onStopMojo(String projectId, String display) {
    }

    protected void onProjectLog(String projectId, String message) {
    }

    private String getProjectId(ExecutionEvent event) {
        return event.getProject().getArtifactId();
    }

    private String getProjectDisplay(ExecutionEvent event) {
        String projectId = getProjectId(event);
        return event.getMojoExecution() != null
                ? ":" + projectId + ":" + event.getMojoExecution().toString()
                : ":" + projectId;
    }

}
