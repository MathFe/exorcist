package dev.exorcist.git;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorIdentitiesTest {

    private final AuthorIdentities identities = new AuthorIdentities();

    @Test
    void mergesIdentitiesThatMeetThroughOthers() {
        // The JUnit 4 case: dsaff <dsaff> and David Saff <saff@google.com> meet through dsaff <david@saff.net>.
        identities.add("dsaff", "dsaff");
        identities.add("dsaff", "david@saff.net");
        identities.add("David Saff", "david@saff.net");
        identities.add("David Saff", "saff@google.com");
        identities.add("David Saff", "saff@google.com");

        assertThat(identities.canonicalNames()).containsOnly(
                Map.entry("dsaff", "David Saff"),
                Map.entry("David Saff", "David Saff"));
    }

    @Test
    void matchesNamesAndEmailsRegardlessOfCase() {
        identities.add("Kent Beck", "KENT@example.com");
        identities.add("kbeck", "kent@example.com");
        identities.add("kent beck", "other@example.com");

        assertThat(identities.canonicalNames().values()).containsOnly("Kent Beck");
    }

    @Test
    void neverMergesStrangersThroughSharedPlaceholderEmails() {
        identities.add("Alice", "noreply@github.com");
        identities.add("Bob", "noreply@github.com");
        identities.add("Carol", "unknown");
        identities.add("Dave", "");

        assertThat(identities.canonicalNames()).containsOnly(
                Map.entry("Alice", "Alice"), Map.entry("Bob", "Bob"),
                Map.entry("Carol", "Carol"), Map.entry("Dave", "Dave"));
    }

    @Test
    void breaksTiesBetweenNamesAlphabetically() {
        identities.add("Robert", "bob@example.com");
        identities.add("Bob", "bob@example.com");

        assertThat(identities.canonicalNames()).containsEntry("Robert", "Bob");
    }
}
