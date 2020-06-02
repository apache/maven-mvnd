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
