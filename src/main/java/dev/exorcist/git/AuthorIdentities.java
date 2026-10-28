package dev.exorcist.git;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Merges the many identities one person accumulates over the years of a repository.
 *
 * <p>Names and e-mails are nodes of a union-find: two identities belong to the same person when they
 * share a name or an e-mail, directly or through others. In JUnit 4, {@code dsaff <dsaff>} and
 * {@code David Saff <saff@google.com>} meet through {@code dsaff <david@saff.net>}. Each person is
 * shown by the name they used most.
 */
final class AuthorIdentities {

    /** Placeholder e-mails shared by unrelated people; linking through them would merge strangers. */
    private static final Set<String> SHARED_MAILBOXES = Set.of("noreply", "no-reply", "none", "unknown", "nobody", "root");

    private final Map<String, String> parent = new HashMap<>();
    private final Map<String, Integer> nameUses = new HashMap<>();

    void add(String name, String email) {
        nameUses.merge(name, 1, Integer::sum);
        String nameNode = nameNode(name);
        find(nameNode);
        if (isLinkable(email)) {
            union(nameNode, "email:" + email.strip().toLowerCase(Locale.ROOT));
        }
    }

    /** Maps every name seen to the display name of the person behind it. */
    Map<String, String> canonicalNames() {
        Map<String, List<String>> namesByPerson = nameUses.keySet().stream()
                .collect(Collectors.groupingBy(name -> find(nameNode(name))));
        Map<String, String> canonical = new HashMap<>();
        for (List<String> names : namesByPerson.values()) {
            String display = names.stream()
                    .max(Comparator.comparingInt((String n) -> nameUses.get(n)).thenComparing(Comparator.reverseOrder()))
                    .orElseThrow();
            names.forEach(name -> canonical.put(name, display));
        }
        return canonical;
    }

    private static String nameNode(String name) {
        return "name:" + name.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean isLinkable(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String mailbox = email.strip().toLowerCase(Locale.ROOT);
        int at = mailbox.indexOf('@');
        return !SHARED_MAILBOXES.contains(at < 0 ? mailbox : mailbox.substring(0, at));
    }

    private String find(String node) {
        String root = node;
        while (true) {
            String up = parent.putIfAbsent(root, root);
            if (up == null || up.equals(root)) {
                break;
            }
            root = up;
        }
        // Path compression keeps later lookups short.
        for (String n = node; !n.equals(root); ) {
            String next = parent.get(n);
            parent.put(n, root);
            n = next;
        }
        return root;
    }

    private void union(String a, String b) {
        String rootA = find(a);
        String rootB = find(b);
        if (!rootA.equals(rootB)) {
            parent.put(rootB, rootA);
        }
    }
}
