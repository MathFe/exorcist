package dev.exorcist;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.exorcist.code.CodeParserService;
import dev.exorcist.code.ParsedClass;
import dev.exorcist.git.CommitInfo;
import dev.exorcist.git.FileSnapshot;
import dev.exorcist.git.GhostProfile;

/** A small haunted class, with known line numbers, shared by the tests. */
public final class Fixtures {

    /**
     * Line numbers the tests rely on: license 1-3, package 4, Javadoc 8, class 9-30, field {@code count} 10,
     * {@code Vector} field 11, constructor 13-14, {@code run()} 16-22 with a try 18-21 swallowing an
     * {@code Exception} at 20-21, TODO comment 24, {@code log()} 25-27 printing at 26, enum 29.
     */
    public static final String COUNTER = """
            /*
             * Licensed to the living.
             */
            package org.example;

            import java.util.Vector;

            /** Counts sheep. It never sleeps. */
            public class Counter extends Object implements Runnable {
                public int count;
                private final Vector<String> names = new Vector<>();

                public Counter() {
                }

                @Override
                public void run() {
                    try {
                        count++;
                    } catch (Exception e) {
                    }
                }

                // TODO make this thread safe
                void log() {
                    System.out.println(count);
                }

                enum Mood { CALM, ANGRY }
            }
            """;

    public static final String PATH = "src/main/java/org/example/Counter.java";

    private Fixtures() {
    }

    public static ZonedDateTime day(int year, int month, int day) {
        return ZonedDateTime.of(year, month, day, 23, 30, 0, 0, ZoneOffset.UTC);
    }

    public static CommitInfo commit(String id, String author, ZonedDateTime when, String message) {
        return new CommitInfo(id.repeat(40).substring(0, 40), author, when, message);
    }

    public static ParsedClass counter() {
        return new CodeParserService().parse(COUNTER, "Counter");
    }

    /** Counter, born in 2001 by Alice, touched by Bob in 2003 and haunted by Alice; Bob last wrote lines 18-21. */
    public static Possession possession() {
        GhostProfile ghost = new GhostProfile("Counter", PATH, List.of(
                commit("c", "Alice", day(2004, 2, 29), "Count harder"),
                commit("b", "Bob", day(2003, 6, 1), "Catch everything"),
                commit("a", "Alice", day(2001, 10, 31), "Birth of the counter")));
        ParsedClass parsed = counter();
        List<FileSnapshot.LineBlame> blame = new ArrayList<>(Collections.nCopies(
                parsed.lines().size(), new FileSnapshot.LineBlame("Alice", day(2001, 10, 31))));
        for (int line = 18; line <= 21; line++) {
            blame.set(line - 1, new FileSnapshot.LineBlame("Bob", day(2003, 6, 1)));
        }
        return new Possession(ghost, new FileSnapshot(COUNTER, blame), parsed);
    }
}
