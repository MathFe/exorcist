package dev.exorcist.ai;

import java.net.ConnectException;

import dev.exorcist.ExorcistException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The only class that talks to the model. Everything else builds prompts or renders answers,
 * which keeps this easy to mock in tests.
 */
@Service
public class GhostService {

    private static final String SEANCE_SYSTEM_PROMPT = """
            You are a ghost haunting an old Java codebase, reached through a seance.
            Answer in at most three short sentences, in a dramatic but friendly tone.
            """;

    private final ChatClient chatClient;
    private final String model;
    private final String baseUrl;

    public GhostService(ChatClient.Builder chatClientBuilder,
                        @Value("${spring.ai.ollama.chat.options.model}") String model,
                        @Value("${spring.ai.ollama.base-url}") String baseUrl) {
        this.chatClient = chatClientBuilder.build();
        this.model = model;
        this.baseUrl = baseUrl;
    }

    public String model() {
        return model;
    }

    /** Smoke test for the model connection: sends a single message and returns the reply. */
    public String seance(String message) {
        return ask(SEANCE_SYSTEM_PROMPT, message);
    }

    private String ask(String system, String user) {
        try {
            return chatClient.prompt()
                    .system(system)
                    .user(user)
                    .call()
                    .content();
        } catch (NonTransientAiException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                throw new ExorcistException(
                        "The spirit '%s' has never been summoned to this machine. Run: ollama pull %s"
                                .formatted(model, model), e);
            }
            throw new ExorcistException("The spirit refused to answer: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            if (hasCause(e, ConnectException.class)) {
                throw new ExorcistException(
                        "The spirit world is silent... Ollama is not answering at %s. Start it with: ollama serve"
                                .formatted(baseUrl), e);
            }
            throw e;
        }
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}
