package com.nexa.user;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Matches a name as written in a transcript ("John", "Sarah Lee", "mike@acme.com") to exactly
 * one organization member. Never guesses: if a name matches zero or several members, there is
 * no match (PRD sections 4.2 and 13 - never invent an owner).
 */
@Component
public class MemberNameResolver {

    public sealed interface Resolution {
        record Matched(User user) implements Resolution {
        }

        record Ambiguous(List<User> candidates) implements Resolution {
        }

        record NoMatch() implements Resolution {
        }
    }

    /**
     * Tries progressively looser rules (email, then full name, then first name) and stops at the
     * first rule that matches anyone, so "Sarah Lee" is never overridden by a looser first-name rule.
     */
    public Resolution resolve(String rawName, List<User> members) {
        String name = normalize(rawName);
        if (name.isEmpty()) {
            return new Resolution.NoMatch();
        }
        // Email only matches in full: a local part like "john@" says nothing about which John.
        List<Predicate<User>> rules = List.of(
                u -> u.getEmail().equalsIgnoreCase(rawName.trim()),
                u -> normalize(u.getName()).equals(name),
                u -> !name.contains(" ") && firstToken(normalize(u.getName())).equals(name));

        for (Predicate<User> rule : rules) {
            List<User> matches = members.stream().filter(User::isActive).filter(rule).toList();
            if (matches.size() == 1) {
                return new Resolution.Matched(matches.getFirst());
            }
            if (matches.size() > 1) {
                return new Resolution.Ambiguous(matches);
            }
        }
        return new Resolution.NoMatch();
    }

    public Optional<User> resolveUnique(String rawName, List<User> members) {
        return resolve(rawName, members) instanceof Resolution.Matched(User user) ? Optional.of(user) : Optional.empty();
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String stripped = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return stripped.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static String firstToken(String normalized) {
        int space = normalized.indexOf(' ');
        return space < 0 ? normalized : normalized.substring(0, space);
    }
}
