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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.builder.DependencyGraph.DagWidth;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DagWidthTest {

    @Test
    void testSimpleGraph() {
        DependencyGraph<String> graph = newSimpleGraph();
        assertEquals(4, new DagWidth<>(graph).getMaxWidth(12));
    }

    /**
     * <pre>
     *   A   B
     *  /|\ / \
     * C D E   F
     *  \|
     *   G
     * </pre>
     */
    private DependencyGraph<String> newSimpleGraph() {
        return newGraph(
                "A", Collections.emptyList(),
                "B", Collections.emptyList(),
                "C", Collections.singletonList("A"),
                "D", Collections.singletonList("A"),
                "E", Arrays.asList("A", "B"),
                "F", Collections.singletonList("B"),
                "G", Arrays.asList("D", "E"));
    }

    @Test
    void tripleLinearGraph() {
        DependencyGraph<String> graph = newTripleLinearGraph();
        assertEquals(1, new DagWidth<>(graph).getMaxWidth());
    }

    /**
     * <pre>
     *   A
     *  /|
     * B |
     *  \|
     *   C
     * </pre>
     */
    private DependencyGraph<String> newTripleLinearGraph() {
        return newGraph(
                "A", Collections.emptyList(),
                "B", Collections.singletonList("A"),
                "C", Arrays.asList("A", "B"));
    }

    @Test
    void quadrupleLinearGraph() {
        DependencyGraph<String> graph = newQuadrupleLinearGraph();
        assertEquals(1, new DagWidth<>(graph).getMaxWidth());
    }

    /**
     * <pre>
     *   A
     *  /|\
     * B | |
     *  \| |
     *   C |
     *    \|
     *     D
     * </pre>
     */
    private DependencyGraph<String> newQuadrupleLinearGraph() {
        return newGraph(
                "A", Collections.emptyList(),
                "B", Collections.singletonList("A"),
                "C", Arrays.asList("B", "A"),
                "D", Arrays.asList("C", "A"));
    }

    @Test
    void quadrupleLinearGraph2() {
        DependencyGraph<String> graph = newQuadrupleLinearGraph2();
        assertEquals(1, new DagWidth<>(graph).getMaxWidth());
    }

    /**
     * <pre>
     *   A
     *  /|\
     * B | |
     * |\| |
     * | C |
     *  \|/
     *   D
     * </pre>
     */
    private DependencyGraph<String> newQuadrupleLinearGraph2() {
        return newGraph(
                "A", Collections.emptyList(),
                "B", Collections.singletonList("A"),
                "C", Arrays.asList("B", "A"),
                "D", Arrays.asList("B", "C", "A"));
    }

    @Test
    void multilevelSum() {
        DependencyGraph<String> graph = newMultilevelSumGraph();
        assertEquals(5, new DagWidth<>(graph).getMaxWidth());
    }

    /**
     * <pre>
     *     A
     *    /|\
     *   B C D
     *  /|\ \|
     * E F G H
     * </pre>
     */
    private DependencyGraph<String> newMultilevelSumGraph() {
        return newGraph(
                "A", Collections.emptyList(),
                "B", Collections.singletonList("A"),
                "C", Collections.singletonList("A"),
                "D", Collections.singletonList("A"),
                "E", Collections.singletonList("B"),
                "F", Collections.singletonList("B"),
                "G", Collections.singletonList("B"),
                "H", Arrays.asList("C", "D"));
    }

    @Test
    void wideGraph() {
        DependencyGraph<String> graph = newWideGraph();
        assertEquals(3, new DagWidth<>(graph).getMaxWidth());
    }

    /**
     * <pre>
     *     A
     *    /|\
     *   B C D
     *       |
     *       E
     * </pre>
     */
    private DependencyGraph<String> newWideGraph() {
        return newGraph(
                "A", Collections.emptyList(),
                "B", Collections.singletonList("A"),
                "C", Collections.singletonList("A"),
                "D", Collections.singletonList("A"),
                "E", Collections.singletonList("D"));
    }

    @Test
    void testSingle() {
        DependencyGraph<String> graph = newSingleGraph();

        assertEquals(1, new DagWidth<>(graph).getMaxWidth(12));
    }

    /**
     * <pre>
     * A
     * </pre>
     */
    private DependencyGraph<String> newSingleGraph() {
        return newGraph("A", Collections.emptyList());
    }

    @Test
    void testLinear() {
        DependencyGraph<String> graph = newLinearGraph();
        assertEquals(1, new DagWidth<>(graph).getMaxWidth(12));
    }

    /**
     * <pre>
     * A
     *         |
     *         B
     *         |
     *         C
     *         |
     *         D
     * </pre>
     */
    private DependencyGraph<String> newLinearGraph() {
        return newGraph(
                "A", Collections.emptyList(),
                "B", Collections.singletonList("A"),
                "C", Collections.singletonList("B"),
                "D", Collections.singletonList("C"));
    }

    @Test
    public void testHugeGraph() {
        DependencyGraph<String> graph = newHugeGraph();

        DagWidth<String> w = new DagWidth<>(graph);
        List<String> d = w.ensembleWithChildrenOf(
                graph.getDownstreamProjects("org.apache.camel:camel").collect(Collectors.toList()),
                "org.apache.camel:camel-parent");

        assertEquals(12, w.getMaxWidth(12));
    }

    private DependencyGraph<String> newHugeGraph() {
        Map<String, List<String>> upstreams = new HashMap<>();
        try (BufferedReader r =
                new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("huge-graph.properties")))) {
            r.lines().forEach(l -> {
                int idxEq = l.indexOf(" = ");
                if (!l.startsWith("#") && idxEq > 0) {
                    String k = l.substring(0, idxEq).trim();
                    String[] ups = l.substring(idxEq + 3).trim().split(",");
                    List<String> list = Stream.of(ups)
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                    upstreams.put(k, list);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return newGraph(upstreams);
    }

    @Test
    public void reduce() {
        assertSameReduced(newSimpleGraph());

        assertReduced(
                newTripleLinearGraph(),
                "A",
                Collections.emptyList(),
                "B",
                Collections.singletonList("A"),
                "C",
                Arrays.asList("B"));

        assertReduced(
                newQuadrupleLinearGraph(),
                "A",
                Collections.emptyList(),
                "B",
                Collections.singletonList("A"),
                "C",
                Arrays.asList("B"),
                "D",
                Arrays.asList("C"));

        assertReduced(
                newQuadrupleLinearGraph2(),
                "A",
                Collections.emptyList(),
                "B",
                Collections.singletonList("A"),
                "C",
                Arrays.asList("B"),
                "D",
                Arrays.asList("C"));

        assertSameReduced(newMultilevelSumGraph());

        assertSameReduced(newWideGraph());

        assertSameReduced(newSingleGraph());

        assertSameReduced(newLinearGraph());
    }

    @Test
    public void testToString() {
        DependencyGraph<String> graph = newSingleGraph();
        assertEquals("A = " + System.lineSeparator(), graph.toString());
    }

    @SuppressWarnings("unchecked")
    static DependencyGraph<String> newGraph(Object... upstreams) {
        final Map<String, List<String>> upstreamsMap = new HashMap<>();
        for (int i = 0; i < upstreams.length; i++) {
            upstreamsMap.put((String) upstreams[i++], (List<String>) upstreams[i]);
        }
        return newGraph(upstreamsMap);
    }

    static <K> DependencyGraph<K> newGraph(Map<K, List<K>> upstreams) {
        List<K> nodes = Stream.concat(
                        upstreams.keySet().stream(), upstreams.values().stream().flatMap(List::stream))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        Map<K, List<K>> downstreams = nodes.stream().collect(Collectors.toMap(k -> k, k -> new ArrayList<>()));
        upstreams.forEach((k, ups) -> {
            ups.forEach(up -> downstreams.get(up).add(k));
        });
        return new DependencyGraph<>(nodes, upstreams, downstreams);
    }

    static void assertReduced(DependencyGraph<String> graph, Object... expectedUpstreams) {
        final DependencyGraph<String> reduced = graph.reduce();
        final DependencyGraph<String> expectedGraph = newGraph(expectedUpstreams);
        assertEquals(expectedGraph, reduced);
    }

    static void assertSameReduced(DependencyGraph<String> graph) {
        final DependencyGraph<String> reduced = graph.reduce();
        assertEquals(graph, reduced);
    }
}
