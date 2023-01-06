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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;

public class TestProjectDependencyGraph implements ProjectDependencyGraph {

    private final List<MavenProject> projects = new ArrayList<MavenProject>();

    private final ListMultimap<MavenProject, MavenProject> downstream = ArrayListMultimap.create();

    private final ListMultimap<MavenProject, MavenProject> upstream = ArrayListMultimap.create();

    public TestProjectDependencyGraph(MavenProject... projects) {
        if (projects != null) {
            this.projects.addAll(Arrays.asList(projects));
        }
    }

    @Override
    public List<MavenProject> getAllProjects() {
        return projects;
    }

    @Override
    public List<MavenProject> getSortedProjects() {
        return projects;
    }

    @Override
    public List<MavenProject> getDownstreamProjects(MavenProject project, boolean transitive) {
        Assertions.assertFalse(transitive, "not implemented");
        return downstream.get(project);
    }

    @Override
    public List<MavenProject> getUpstreamProjects(MavenProject project, boolean transitive) {
        Assertions.assertFalse(transitive, "not implemented");
        return upstream.get(project);
    }

    public void addProject(MavenProject project) {
        projects.add(project);
    }

    public void addDependency(MavenProject from, MavenProject to) {
        // 'from' depends on 'to'
        // 'from' is a downstream dependency of 'to'
        // 'to' is upstream dependency of 'from'
        this.upstream.put(from, to);
        this.downstream.put(to, from);
    }
}
