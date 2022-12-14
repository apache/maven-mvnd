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

import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ReactorBuildQueueTest extends AbstractSmartBuilderTest {

    @Test
    public void testBasic() {
        MavenProject a = newProject("a"), b = newProject("b"), c = newProject("c");
        TestProjectDependencyGraph graph = new TestProjectDependencyGraph(a, b, c);
        graph.addDependency(b, a);
        DependencyGraph<MavenProject> dp = DependencyGraph.fromMaven(graph);

        ReactorBuildQueue schl = new ReactorBuildQueue(graph.getSortedProjects(), dp);

        assertProjects(schl.getRootProjects(), a, c);
        Assertions.assertFalse(schl.isEmpty());

        assertProjects(schl.onProjectFinish(a), b);
        Assertions.assertTrue(schl.isEmpty());
    }

    @Test
    public void testNoDependencies() {
        MavenProject a = newProject("a"), b = newProject("b"), c = newProject("c");
        TestProjectDependencyGraph graph = new TestProjectDependencyGraph(a, b, c);
        DependencyGraph<MavenProject> dp = DependencyGraph.fromMaven(graph);

        ReactorBuildQueue schl = new ReactorBuildQueue(graph.getSortedProjects(), dp);

        assertProjects(schl.getRootProjects(), a, b, c);
        Assertions.assertTrue(schl.isEmpty());
    }

    @Test
    public void testMultipleUpstreamDependencies() {
        MavenProject a = newProject("a"), b = newProject("b"), c = newProject("c");
        TestProjectDependencyGraph graph = new TestProjectDependencyGraph(a, b, c);
        graph.addDependency(b, a);
        graph.addDependency(b, c);
        DependencyGraph<MavenProject> dp = DependencyGraph.fromMaven(graph);

        ReactorBuildQueue schl = new ReactorBuildQueue(graph.getSortedProjects(), dp);

        assertProjects(schl.getRootProjects(), a, c);
        Assertions.assertFalse(schl.isEmpty());

        assertProjects(schl.onProjectFinish(a), new MavenProject[0]);
        Assertions.assertFalse(schl.isEmpty());

        assertProjects(schl.onProjectFinish(c), b);
        Assertions.assertTrue(schl.isEmpty());
    }
}
