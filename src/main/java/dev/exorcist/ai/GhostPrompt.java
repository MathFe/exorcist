package dev.exorcist.ai;

/** A ready-to-send prompt: who the model is ({@code system}) and what it is asked ({@code user}). */
public record GhostPrompt(String system, String user) {
}
