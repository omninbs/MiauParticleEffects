package org.miau.particleeffects.animation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class AnimationSet {

    public static final AnimationSet EMPTY = new AnimationSet(List.of());

    private final List<AnimationToken> tokens;

    private AnimationSet(List<AnimationToken> tokens) {
        this.tokens = List.copyOf(tokens);
    }

    public List<AnimationToken> tokens() {
        return tokens;
    }

    public boolean isEmpty() {
        return tokens.isEmpty();
    }

    public Optional<AnimationToken> of(AnimCategory category) {
        for (AnimationToken token : tokens) {
            if (token.category() == category) {
                return Optional.of(token);
            }
        }
        return Optional.empty();
    }

    public static AnimationSet of(List<AnimationToken> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return EMPTY;
        }
        Set<AnimCategory> seen = new HashSet<>();
        for (AnimationToken token : tokens) {
            if (!seen.add(token.category())) {
                throw new IllegalArgumentException("同类动画互斥，不能组合 2 个以上同类动画 '" + token.category() + "'");
            }
        }
        return new AnimationSet(tokens);
    }

    public static AnimationSet parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }
        List<AnimationToken> list = new ArrayList<>();
        for (String part : raw.split(",")) {
            String p = part.trim().toLowerCase(Locale.ROOT);
            if (!p.isEmpty()) {
                list.add(AnimationToken.parse(p));
            }
        }
        return of(list);
    }

    public boolean containsOnlyCategories(AnimCategory... allowed) {
        for (AnimationToken token : tokens) {
            boolean ok = false;
            for (AnimCategory category : allowed) {
                if (token.category() == category) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return tokens.toString();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof AnimationSet other && tokens.equals(other.tokens));
    }

    @Override
    public int hashCode() {
        return tokens.hashCode();
    }
}