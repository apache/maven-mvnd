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
package org.jboss.fuse.mvnd.assertj;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.Condition;

/**
 * An AssertJ {@link Condition} to assert that each item of a collection of regular expressions matches some item in
 * a list of strings exactly once in the order given by the pattern collection. The input list may contain other
 * non-matching items.
 *
 * @param <T> the type of the tested {@link List}.
 */
public class MatchInOrderAmongOthers<T extends List<? extends String>> extends Condition<T> {

    public MatchInOrderAmongOthers(String... expectedItems) {
        this(Stream.of(expectedItems).map(Pattern::compile).collect(Collectors.toList()));
    }

    public MatchInOrderAmongOthers(final Collection<Pattern> patterns) {
        super(
                messages -> messages.stream()
                        /* map each message to the matching pattern or null of none matches */
                        .map(m -> patterns.stream()
                                .filter(pat -> pat.matcher(m).find())
                                .findFirst()
                                .orElse(null))
                        .filter(pat -> pat != null) /* remove null patterns */
                        .collect(Collectors.toList())
                        /* if the mapped patterns equal the input patterns then each pattern matched exactly once */
                        .equals(patterns),
                "Match in order: " + patterns.stream().map(Pattern::pattern).collect(Collectors.joining(", ")),
                patterns);
    }

}
