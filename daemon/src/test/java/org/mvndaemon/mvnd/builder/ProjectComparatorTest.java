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

import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mvndaemon.mvnd.builder.ProjectComparator.id;

public class ProjectComparatorTest extends AbstractSmartBuilderTest {

    @Test
    public void testPriorityQueueOrder() {
        MavenProject a = newProject("a"), b = newProject("b"), c = newProject("c");
        TestProjectDependencyGraph graph = new TestProjectDependencyGraph(a, b, c);
        graph.addDependency(b, a);
        DependencyGraph<MavenProject> dp = DependencyGraph.fromMaven(graph);

        Comparator<MavenProject> cmp = ProjectComparator.create0(dp, new HashMap<>(), ProjectComparator::id);

        Queue<MavenProject> queue = new PriorityQueue<>(3, cmp);
        queue.add(a);
        queue.add(b);
        queue.add(c);

        Assertions.assertEquals(a, queue.poll());
        Assertions.assertEquals(c, queue.poll());
        Assertions.assertEquals(b, queue.poll());
    }

    @Test
    public void testPriorityQueueOrder_historicalServiceTimes() {
        MavenProject a = newProject("a"), b = newProject("b"), c = newProject("c");
        TestProjectDependencyGraph graph = new TestProjectDependencyGraph(a, b, c);
        graph.addDependency(b, a);
        DependencyGraph<MavenProject> dp = DependencyGraph.fromMaven(graph);

        HashMap<String, AtomicLong> serviceTimes = new HashMap<>();
        serviceTimes.put(id(a), new AtomicLong(1L));
        serviceTimes.put(id(b), new AtomicLong(1L));
        serviceTimes.put(id(c), new AtomicLong(3L));

        Comparator<MavenProject> cmp = ProjectComparator.create0(dp, serviceTimes, ProjectComparator::id);

        Queue<MavenProject> queue = new PriorityQueue<>(3, cmp);
        queue.add(a);
        queue.add(b);
        queue.add(c);

        Assertions.assertEquals(c, queue.poll());
        Assertions.assertEquals(a, queue.poll());
        Assertions.assertEquals(b, queue.poll());
    }
}
