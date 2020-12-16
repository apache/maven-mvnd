/*
 * Copyright 2017 the original author or authors.
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

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File origin:
 * https://github.com/takari/takari-smart-builder/blob/takari-smart-builder-0.6.1/src/main/java/io/takari/maven/builder/smart/DependencyGraph.java
 */
public class DependencyGraph<K> {

    private static final Logger logger = LoggerFactory.getLogger(DependencyGraph.class);
    static final Pattern mvndRuleSanitizerPattern = Pattern.compile("[,\\s]+");

    private final List<K> projects;
    private final Map<K, List<K>> upstreams;
    private final Map<K, List<K>> downstreams;

    public static DependencyGraph<MavenProject> fromMaven(MavenSession session) {

        final ProjectDependencyGraph graph = session.getProjectDependencyGraph();
        final List<MavenProject> projects = graph.getSortedProjects();
        return fromMaven(graph, getRules(projects, session));
    }

    static String getRules(List<MavenProject> projects, MavenSession session) {
        List<String> list = new ArrayList<>();

        String providerScript = null;
        final MavenProject topLevelProject = projects.get(0);
        String providerUrl = topLevelProject.getProperties()
                .getProperty(SmartBuilder.MVND_BUILDER_RULES_PROVIDER_URL);
        if (providerUrl != null) {
            logger.warn(SmartBuilder.MVND_BUILDER_RULES_PROVIDER_URL
                    + " property is deprecated and the support for it will be removed in mvnd 0.3. See https://github.com/mvndaemon/mvnd/issues/264");

            URL url;
            try {
                url = new URL(providerUrl);
            } catch (MalformedURLException e) {
                try {
                    url = new File(providerUrl).toURI().toURL();
                } catch (MalformedURLException ex) {
                    url = null;
                }
            }
            if (url == null) {
                throw new RuntimeException("Bad syntax for " + SmartBuilder.MVND_BUILDER_RULES_PROVIDER_URL, null);
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream()))) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                int l;
                while ((l = r.read(buf)) >= 0) {
                    sb.append(buf, 0, l);
                }
                providerScript = sb.toString();
            } catch (IOException e) {
                throw new RuntimeException("Unable to read provider url " + SmartBuilder.MVND_BUILDER_RULES_PROVIDER_URL,
                        e);
            }
        }
        if (providerScript == null) {
            providerScript = topLevelProject.getProperties()
                    .getProperty(SmartBuilder.MVND_BUILDER_RULES_PROVIDER_SCRIPT);
        }
        if (providerScript != null) {
            logger.warn(SmartBuilder.MVND_BUILDER_RULES_PROVIDER_SCRIPT
                    + " property is deprecated and the support for it will be removed in mvnd 0.3. See https://github.com/mvndaemon/mvnd/issues/264");

            Binding binding = new Binding();
            GroovyShell shell = new GroovyShell(binding);
            binding.setProperty("session", session);
            Object result = shell.evaluate(providerScript);
            if (result instanceof Iterable) {
                for (Object r : (Iterable) result) {
                    list.add(r.toString());
                }
            } else if (result != null) {
                list.add(result.toString());
            } else {
                throw new RuntimeException("The provider script did not return a valid string or string collection", null);
            }
            list.add(result.toString());
        }

        String topRule = topLevelProject.getProperties().getProperty(SmartBuilder.MVND_BUILDER_RULES);
        if (topRule != null) {
            logger.warn(SmartBuilder.MVND_BUILDER_RULES
                    + " property is deprecated and the support for it will be removed in mvnd 0.3. See https://github.com/mvndaemon/mvnd/issues/264");
            list.add(topRule);
        }

        projects.forEach(p -> {
            String rule = p.getProperties().getProperty(SmartBuilder.MVND_BUILDER_RULE);
            if (rule != null) {
                logger.warn(SmartBuilder.MVND_BUILDER_RULE
                        + " property is deprecated and the support for it will be removed in mvnd 0.3. See https://github.com/mvndaemon/mvnd/issues/264");
                rule = rule.trim();
                if (!rule.isEmpty()) {
                    rule = mvndRuleSanitizerPattern.matcher(rule).replaceAll(",");
                    list.add(rule + " before " + p.getGroupId() + ":" + p.getArtifactId());
                }
            }
        });
        String rules = null;
        if (!list.isEmpty()) {
            rules = String.join("\n", list);
        }
        return rules;
    }

    static DependencyGraph<MavenProject> fromMaven(ProjectDependencyGraph graph, String rules) {
        final List<MavenProject> projects = graph.getSortedProjects();
        Map<MavenProject, List<MavenProject>> upstreams = projects.stream()
                .collect(Collectors.toMap(p -> p, p -> graph.getUpstreamProjects(p, false)));
        Map<MavenProject, List<MavenProject>> downstreams = projects.stream()
                .collect(
                        Collectors.toMap(p -> p, p -> graph.getDownstreamProjects(p, false)));

        if (rules != null) {
            for (String rule : rules.split("\\s*;\\s*|\n")) {
                if (rule.trim().isEmpty()) {
                    continue;
                }
                String[] parts = rule.split("\\s*->\\s*|\\s+before\\s+");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid rule: " + rule);
                }
                List<Set<MavenProject>> deps = Stream.of(parts).map(s -> Pattern.compile(
                        Arrays.stream(s.split("\\s*,\\s*|\\s+and\\s+"))
                                .map(String::trim)
                                .map(r -> r.contains(":") ? r : "*:" + r)
                                .map(r -> r.replaceAll("\\.", "\\.")
                                        .replaceAll("\\*", ".*"))
                                .collect(Collectors.joining("|"))))
                        .map(t -> projects.stream()
                                .filter(p -> t.matcher(p.getGroupId() + ":" + p.getArtifactId()).matches())
                                .collect(Collectors.toSet()))
                        .collect(Collectors.toList());

                Set<MavenProject> common = deps.get(0).stream().filter(deps.get(1)::contains).collect(Collectors.toSet());
                if (!common.isEmpty()) {
                    boolean leftWildcard = parts[0].contains("*");
                    boolean rightWildcard = parts[1].contains("*");
                    if (leftWildcard && rightWildcard) {
                        throw new IllegalArgumentException("Invalid rule: " + rule
                                + ".  Both left and right parts have wildcards and match the same project.");
                    } else if (leftWildcard) {
                        deps.get(0).removeAll(common);
                    } else if (rightWildcard) {
                        deps.get(1).removeAll(common);
                    } else {
                        throw new IllegalArgumentException(
                                "Invalid rule: " + rule + ". Both left and right parts match the same project.");
                    }
                }

                deps.get(1).forEach(p -> upstreams.get(p).addAll(deps.get(0)));
                deps.get(0).forEach(p -> downstreams.get(p).addAll(deps.get(1)));
            }
        }
        return new DependencyGraph<MavenProject>(Collections.unmodifiableList(projects), upstreams, downstreams);
    }

    public DependencyGraph(List<K> projects, Map<K, List<K>> upstreams, Map<K, List<K>> downstreams) {
        this.projects = projects;
        this.upstreams = upstreams;
        this.downstreams = downstreams;
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
            getProjects().forEach(k -> {
                try {
                    w.write(toString.apply(k));
                    w.write(" = ");
                    w.write(getUpstreamProjects(k).map(toString).collect(Collectors.joining(",")));
                    w.write(System.lineSeparator());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class DagWidth<K> {

        private final DependencyGraph<K> graph;
        private final Map<K, Set<K>> allUpstreams = new HashMap<>();

        public DagWidth(DependencyGraph<K> graph) {
            this.graph = graph;
            graph.getProjects().forEach(this::allUpstreams);
        }

        public int getMaxWidth() {
            return getMaxWidth(Integer.MAX_VALUE);
        }

        public int getMaxWidth(int maxmax) {
            return getMaxWidth(maxmax, Long.MAX_VALUE);
        }

        public int getMaxWidth(int maxmax, long maxTimeMillis) {
            int max = 0;
            if (maxmax < allUpstreams.size()) {
                // try inverted upstream bound
                Map<Set<K>, Set<K>> mapByUpstreams = new HashMap<>();
                allUpstreams.forEach((k, ups) -> {
                    mapByUpstreams.computeIfAbsent(ups, n -> new HashSet<>()).add(k);
                });
                max = mapByUpstreams.values().stream()
                        .mapToInt(Set::size)
                        .max()
                        .orElse(0);
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
            return graph.getProjects()
                    .filter(graph::isRoot)
                    .collect(Collectors.toList());
        }

        /**
         * Get a stream of all subset of descendants of the given nodes
         */
        private Stream<List<K>> childEnsembles(List<K> list) {
            return Stream.concat(
                    Stream.of(list),
                    list.parallelStream()
                            .map(node -> ensembleWithChildrenOf(list, node))
                            .flatMap(this::childEnsembles));
        }

        List<K> ensembleWithChildrenOf(List<K> list, K node) {
            return Stream.concat(
                    list.stream().filter(k -> !Objects.equals(k, node)),
                    graph.getDownstreamProjects(node)
                            .filter(k -> allUpstreams(k).stream()
                                    .noneMatch(k2 -> !Objects.equals(k2, node) && list.contains(k2))))
                    .distinct().collect(Collectors.toList());
        }

        private Set<K> allUpstreams(K node) {
            Set<K> aups = allUpstreams.get(node);
            if (aups == null) {
                aups = Stream.concat(
                        graph.getUpstreamProjects(node),
                        graph.getUpstreamProjects(node).map(this::allUpstreams).flatMap(Set::stream))
                        .collect(Collectors.toSet());
                allUpstreams.put(node, aups);
            }
            return aups;
        }

    }

}
