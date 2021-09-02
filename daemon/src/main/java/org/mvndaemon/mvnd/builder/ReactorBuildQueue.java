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
package org.mvndaemon.mvnd.builder;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.maven.project.MavenProject;

/**
 * Reactor build queue manages reactor modules that are waiting for their upstream dependencies
 * build to finish.
 *
 * File origin:
 * https://github.com/takari/takari-smart-builder/blob/takari-smart-builder-0.6.1/src/main/java/io/takari/maven/builder/smart/ReactorBuildQueue.java
 */
class ReactorBuildQueue {

    private final DependencyGraph<MavenProject> graph;

    private final Set<MavenProject> rootProjects;

    private final Set<MavenProject> projects;

    /**
     * Projects waiting for other projects to finish
     */
    private final Set<MavenProject> blockedProjects;

    private final Set<MavenProject> finishedProjects;

    public ReactorBuildQueue(Collection<MavenProject> projects,
            DependencyGraph<MavenProject> graph) {
        this.graph = graph;
        this.projects = new HashSet<>();
        this.rootProjects = new HashSet<>();
        this.blockedProjects = new HashSet<>();
        this.finishedProjects = new HashSet<>();
        projects.forEach(project -> {
            this.projects.add(project);
            if (this.graph.isRoot(project)) {
                this.rootProjects.add(project);
            } else {
                this.blockedProjects.add(project);
            }
        });
    }

    /**
     * Marks specified project as finished building. Returns, possible empty, set of project's
     * downstream dependencies that become ready to build.
     */
    public Set<MavenProject> onProjectFinish(MavenProject project) {
        finishedProjects.add(project);
        Set<MavenProject> downstreamProjects = new HashSet<>();
        getDownstreamProjects(project)
                .filter(successor -> blockedProjects.contains(successor) && isProjectReady(successor))
                .forEach(successor -> {
                    blockedProjects.remove(successor);
                    downstreamProjects.add(successor);
                });
        return downstreamProjects;
    }

    public Stream<MavenProject> getDownstreamProjects(MavenProject project) {
        return graph.getDownstreamProjects(project);
    }

    private boolean isProjectReady(MavenProject project) {
        return graph.getUpstreamProjects(project).allMatch(finishedProjects::contains);
    }

    /**
     * Returns {@code true} when no more projects are left to schedule.
     */
    public boolean isEmpty() {
        return blockedProjects.isEmpty();
    }

    /**
     * Returns reactor build root projects, that is, projects that do not have upstream dependencies.
     */
    public Set<MavenProject> getRootProjects() {
        return rootProjects;
    }

    public int getBlockedCount() {
        return blockedProjects.size();
    }

    public int getFinishedCount() {
        return finishedProjects.size();
    }

    public int getReadyCount() {
        return projects.size() - blockedProjects.size() - finishedProjects.size();
    }

    public Set<MavenProject> getReadyProjects() {
        Set<MavenProject> projects = new HashSet<>(this.projects);
        projects.removeAll(blockedProjects);
        projects.removeAll(finishedProjects);
        return projects;
    }

}
