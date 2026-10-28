package dev.exorcist;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limits on what goes into a prompt, so that one class always fits a small model's context window.
 *
 * @param maxSourceLines    source lines sent to the model; longer classes are cut and summarized by their outline
 * @param maxCommitMessages commit messages included as the ghost's memories
 * @param maxOutlineMembers members (fields, methods...) listed in the class outline
 */
@ConfigurationProperties("exorcist")
public record ExorcistProperties(
        @DefaultValue("500") int maxSourceLines,
        @DefaultValue("15") int maxCommitMessages,
        @DefaultValue("80") int maxOutlineMembers) {
}
