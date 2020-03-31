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
package org.jboss.fuse.mvnd.builder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;

import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Test;

public class DependencyGraphTest extends AbstractSmartBuilderTest {

    @Test
    public void testRules() {
        MavenProject a = newProject("a"), b = newProject("b"), c = newProject("c");
        TestProjectDependencyGraph graph = new TestProjectDependencyGraph(a, b, c);
        graph.addDependency(b, a);
        DependencyGraph<MavenProject> dp = DependencyGraph.fromMaven(graph, "a before c");
        Assert.assertEquals(new HashSet<>(Arrays.asList(b, c)),
                dp.getDownstreamProjects(a).collect(Collectors.toSet()));
    }
}
