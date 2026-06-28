package com.sheath.veinminer.logic.rules;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

final class RuleMatcher {

    private RuleMatcher() {
    }

    static <I, T> boolean matches(I id, Set<I> ids, Set<T> tags, Predicate<T> tagMatcher) {
        Objects.requireNonNull(ids, "ids");
        Objects.requireNonNull(tags, "tags");
        Objects.requireNonNull(tagMatcher, "tagMatcher");
        if (ids.contains(id)) {
            return true;
        }
        for (T tag : tags) {
            if (tagMatcher.test(tag)) {
                return true;
            }
        }
        return false;
    }
}
