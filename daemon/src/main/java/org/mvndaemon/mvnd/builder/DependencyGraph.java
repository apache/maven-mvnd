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

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.project.MavenProject;

/**
 * File origin:
 * https://github.com/takari/takari-smart-builder/blob/takari-smart-builder-0.6.1/src/main/java/io/takari/maven/builder/smart/DependencyGraph.java
 */
public class DependencyGraph<K> {

    private final List<K> projects;
    private final Map<K, List<K>> upstreams;
    private final Map<K, Set<K>> transitiveUpstreams;
    private final Map<K, List<K>> downstreams;

    @SuppressWarnings("unchecked")
    public static DependencyGraph<MavenProject> fromMaven(MavenSession session) {
        Map<String, Object> data = session.getRequest().getData();
        DependencyGraph<MavenProject> graph = (DependencyGraph<MavenProject>) data.get(DependencyGraph.class.getName());
        if (graph == null) {
            graph = fromMaven(session.getProjectDependencyGraph());
            data.put(DependencyGraph.class.getName(), graph);
        }
        return graph;
    }

    static DependencyGraph<MavenProject> fromMaven(ProjectDependencyGraph graph) {
        final List<MavenProject> projects = graph.getSortedProjects();
        Map<MavenProject, List<MavenProject>> upstreams =
                projects.stream().collect(Collectors.toMap(p -> p, p -> graph.getUpstreamProjects(p, false)));
        Map<MavenProject, List<MavenProject>> downstreams =
                projects.stream().collect(Collectors.toMap(p -> p, p -> graph.getDownstreamProjects(p, false)));
        return new DependencyGraph<>(Collections.unmodifiableList(projects), upstreams, downstreams);
    }

    public DependencyGraph(List<K> projects, Map<K, List<K>> upstreams, Map<K, List<K>> downstreams) {
        this.projects = projects;
        this.upstreams = upstreams;
        this.downstreams = downstreams;

        this.transitiveUpstreams = new HashMap<>();
        projects.forEach(this::transitiveUpstreams); // topological ordering of projects matters
    }

    DependencyGraph(
            List<K> projects,
            Map<K, List<K>> upstreams,
            Map<K, List<K>> downstreams,
            Map<K, Set<K>> transitiveUpstreams) {
        this.projects = projects;
        this.upstreams = upstreams;
        this.downstreams = downstreams;
        this.transitiveUpstreams = transitiveUpstreams;
    }

    public Stream<K> getDownstreamProjects(K project) {
        return downstreams.get(project).stream();
    }

    public Stream<K> getUpstreamProjects(K project) {
        return upstreams.get(project).stream();
    }

    public boolean isRoot(K project) {
        return upstreams.get(project).isEmpty();
    }

    public Stream<K> getProjects() {
        return projects.stream();
    }

    public int computeMaxWidth(int max, long maxTimeMillis) {
        return new DagWidth<>(this).getMaxWidth(max, maxTimeMillis);
    }

    public void store(Function<K, String> toString, Path path) {
        try (Writer w = Files.newBufferedWriter(path)) {
            store(toString, w);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void store(Function<K, String> toString, Appendable w) {
        getProjects().forEach(k -> {
            try {
                w.append(toString.apply(k));
                w.append(" = ");
                w.append(getUpstreamProjects(k).map(toString).collect(Collectors.joining(",")));
                w.append(System.lineSeparator());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        store(k -> k.toString(), sb);
        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((downstreams == null) ? 0 : downstreams.hashCode());
        result = prime * result + ((projects == null) ? 0 : projects.hashCode());
        result = prime * result + ((upstreams == null) ? 0 : upstreams.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        @SuppressWarnings("unchecked")
        DependencyGraph<K> other = (DependencyGraph<K>) obj;
        if (downstreams == null) {
            if (other.downstreams != null) return false;
        } else if (!downstreams.equals(other.downstreams)) return false;
        if (projects == null) {
            if (other.projects != null) return false;
        } else if (!projects.equals(other.projects)) return false;
        if (upstreams == null) {
            if (other.upstreams != null) return false;
        } else if (!upstreams.equals(other.upstreams)) return false;
        return true;
    }

    /**
     * Creates a new {@link DependencyGraph} which is a <a href="https://en.wikipedia.org/wiki/Transitive_reduction">
     * transitive reduction</a> of this {@link DependencyGraph}. The reduction operation keeps the set of graph nodes
     * unchanged and it reduces the set of edges in the following way: An edge {@code C -> A} is removed if an edge
     * {@code C -> B} exists such that {@code A != B} and the set of nodes reachable from {@code B} contains {@code A};
     * otherwise the edge {@code C -> A} is kept in the reduced graph.
     * <p>
     * Examples:
     *
     * <pre>
     * Original     Reduced
     *
     *    A           A
     *   /|          /
     *  B |         B
     *   \|          \
     *    C           C
     *
     *
     *    A           A
     *   /|\         /
     *  B | |       B
     *   \| |        \
     *    C |         C
     *     \|          \
     *      D           D
     *
     * </pre>
     *
     *
     * @return a transitive reduction of this {@link DependencyGraph}
     */
    DependencyGraph<K> reduce() {
        final Map<K, List<K>> newUpstreams = new HashMap<>();
        final Map<K, List<K>> newDownstreams = new HashMap<>();
        for (K node : projects) {
            final List<K> oldNodeUpstreams = upstreams.get(node);
            final List<K> newNodeUpstreams;
            newDownstreams.computeIfAbsent(node, k -> new ArrayList<>());
            if (oldNodeUpstreams.size() == 0) {
                newNodeUpstreams = new ArrayList<>(oldNodeUpstreams);
            } else if (oldNodeUpstreams.size() == 1) {
                newNodeUpstreams = new ArrayList<>(oldNodeUpstreams);
                newDownstreams
                        .computeIfAbsent(newNodeUpstreams.get(0), k -> new ArrayList<>())
                        .add(node);
            } else {
                newNodeUpstreams = new ArrayList<>(oldNodeUpstreams.size());
                for (K leftNode : oldNodeUpstreams) {
                    if (oldNodeUpstreams.stream()
                            .filter(rightNode -> leftNode != rightNode)
                            .noneMatch(rightNode ->
                                    transitiveUpstreams.get(rightNode).contains(leftNode))) {

                        newNodeUpstreams.add(leftNode);
                        newDownstreams
                                .computeIfAbsent(leftNode, k -> new ArrayList<>())
                                .add(node);
                    }
                }
            }
            newUpstreams.put(node, newNodeUpstreams);
        }
        return new DependencyGraph<K>(projects, newUpstreams, newDownstreams, transitiveUpstreams);
    }

    /**
     * Compute the set of nodes reachable from the given {@code node} through the {@code is upstream of} relation. The
     * {@code node} itself is not a part of the returned set.
     *
     * @param  node the node for which the transitive upstream should be computed
     * @return      the set of transitive upstreams
     */
    Set<K> transitiveUpstreams(K node) {
        Set<K> result = transitiveUpstreams.get(node);
        if (result == null) {
            final List<K> firstOrderUpstreams = this.upstreams.get(node);
            result = new HashSet<>(firstOrderUpstreams);
            firstOrderUpstreams.stream().map(this::transitiveUpstreams).forEach(result::addAll);
            transitiveUpstreams.put(node, result);
        }
        return result;
    }

    static class DagWidth<K> {

        private final DependencyGraph<K> graph;

        public DagWidth(DependencyGraph<K> graph) {
            this.graph = graph.reduce();
        }

        public int getMaxWidth() {
            return getMaxWidth(Integer.MAX_VALUE);
        }

        public int getMaxWidth(int maxmax) {
            return getMaxWidth(maxmax, Long.MAX_VALUE);
        }

        public int getMaxWidth(int maxmax, long maxTimeMillis) {
            int max = 0;
            if (maxmax < graph.transitiveUpstreams.size()) {
                // try inverted upstream bound
                Map<Set<K>, Set<K>> mapByUpstreams = new HashMap<>();
                graph.transitiveUpstreams.forEach((k, ups) -> {
                    mapByUpstreams.computeIfAbsent(ups, n -> new HashSet<>()).add(k);
                });
                max = mapByUpstreams.values().stream().mapToInt(Set::size).max().orElse(0);
                if (max >= maxmax) {
                    return maxmax;
                }
            }
            long tmax = System.currentTimeMillis() + maxTimeMillis;
            int tries = 0;
            SubsetIterator iterator = new SubsetIterator(getRoots());
            while (max < maxmax && iterator.hasNext()) {
                if (++tries % 100 == 0 && System.currentTimeMillis() < tmax) {
                    return maxmax;
                }
                List<K> l = iterator.next();
                max = Math.max(max, l.size());
            }
            return Math.min(max, maxmax);
        }

        private class SubsetIterator implements Iterator<List<K>> {

            final List<List<K>> nexts = new ArrayList<>();
            final Set<List<K>> visited = new HashSet<>();

            public SubsetIterator(List<K> roots) {
                nexts.add(roots);
            }

            @Override
            public boolean hasNext() {
                return !nexts.isEmpty();
            }

            @Override
            public List<K> next() {
                List<K> list = nexts.remove(0);
                list.stream()
                        .map(node -> ensembleWithChildrenOf(list, node))
                        .filter(visited::add)
                        .forEach(nexts::add);
                return list;
            }
        }

        private List<K> getRoots() {
            return graph.getProjects().filter(graph::isRoot).collect(Collectors.toList());
        }

        List<K> ensembleWithChildrenOf(List<K> list, K node) {
            final List<K> result = Stream.concat(
                            list.stream().filter(k -> !Objects.equals(k, node)),
                            graph.getDownstreamProjects(node).filter(k -> graph.transitiveUpstreams.get(k).stream()
                                    .noneMatch(k2 -> !Objects.equals(k2, node) && list.contains(k2))))
                    .distinct()
                    .collect(Collectors.toList());
            return result;
        }
    }
}
