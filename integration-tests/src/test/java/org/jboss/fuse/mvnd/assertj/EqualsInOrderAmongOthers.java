package org.jboss.fuse.mvnd.assertj;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.assertj.core.api.Condition;

/**
 * An AssertJ {@link Condition} to assert that each item of a collection of expected items is equal to some item in
 * a list of strings exactly once in the order given by the expected items collection. The input list may contain other
 * non-matching items.
 *
 * @param <T> the type of the tested {@link List}.
 */
public class EqualsInOrderAmongOthers<T extends List<? extends String>> extends Condition<T> {

    public EqualsInOrderAmongOthers(String... expectedItems) {
        this(Stream.of(expectedItems).collect(Collectors.toList()));
    }

    public EqualsInOrderAmongOthers(final Collection<String> expectedItems) {
        super(
                messages -> messages.stream()
                        /* map each message to the matching pattern or null of none matches */
                        .map(m -> expectedItems.stream()
                                .filter(expected -> expected.equals(m))
                                .findFirst()
                                .orElse(null))
                        .filter(pat -> pat != null) /* remove null patterns */
                        .collect(Collectors.toList())
                        /* if the mapped patterns equal the input patterns then each pattern matched exactly once */
                        .equals(expectedItems),
                "Match in order: " + expectedItems.stream().collect(Collectors.joining(", ")),
                expectedItems);
    }

}
