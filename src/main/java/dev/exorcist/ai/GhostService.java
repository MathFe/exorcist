package dev.exorcist.ai;

import java.net.ConnectException;
import java.util.Locale;

import dev.exorcist.ExorcistException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * The only class that talks to the model. Everything else builds prompts or renders answers,
 * which keeps this easy to mock in tests.
 */
@Service
public class GhostService {

    private static final Logger log = LoggerFactory.getLogger(GhostService.class);

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
        try {
            return chatClient.prompt().system(SEANCE_SYSTEM_PROMPT).user(message).call().content();
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    /** Sends the prompt and waits for the whole answer: for short answers that are not worth streaming. */
    public String ask(GhostPrompt prompt) {
        log.debug("Prompt for {}:\n--- SYSTEM ---\n{}\n--- USER ---\n{}", model, prompt.system(), prompt.user());
        try {
            return chatClient.prompt().system(prompt.system()).user(prompt.user()).call().content();
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    /** Sends the prompt and streams the answer as it is generated, so the user is not left staring at a frozen terminal. */
    public Flux<String> stream(GhostPrompt prompt) {
        log.debug("Prompt for {}:\n--- SYSTEM ---\n{}\n--- USER ---\n{}", model, prompt.system(), prompt.user());
        return chatClient.prompt()
                .system(prompt.system())
                .user(prompt.user())
                .stream()
                .content()
                .onErrorMap(this::translate);
    }

    /** Turns connection and model errors into messages that tell the user what to do. */
    private RuntimeException translate(Throwable error) {
        if (error instanceof ExorcistException e) {
            return e;
        }
        if (hasCause(error, ConnectException.class)) {
            return new ExorcistException(
                    "The spirit world is silent... Ollama is not answering at %s. Start it with: ollama serve"
                            .formatted(baseUrl), error);
        }
        if (messagesContain(error, "not found")) {
            return new ExorcistException(
                    "The spirit '%s' has never been summoned to this machine. Run: ollama pull %s"
                            .formatted(model, model), error);
        }
        return new ExorcistException("The spirit refused to answer: " + error.getMessage(), error);
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    private static boolean messagesContain(Throwable error, String text) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().toLowerCase(Locale.ROOT).contains(text)) {
                return true;
            }
        }
        return false;
    }
}
