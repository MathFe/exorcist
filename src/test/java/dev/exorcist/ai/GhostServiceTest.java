package dev.exorcist.ai;

import java.net.ConnectException;

import dev.exorcist.ExorcistException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GhostServiceTest {

    private static final GhostPrompt PROMPT = new GhostPrompt("You are a ghost.", "Explain yourself.");

    private ChatClient chatClient;
    private GhostService ghostService;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);
        ghostService = new GhostService(builder, "qwen2.5-coder:7b", "http://localhost:11434");
    }

    @Test
    void returnsTheModelsAnswer() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("Boo.");

        assertThat(ghostService.ask(PROMPT)).isEqualTo("Boo.");
    }

    @Test
    void streamsTheModelsAnswer() {
        when(chatClient.prompt().system(anyString()).user(anyString()).stream().content())
                .thenReturn(Flux.just("B", "oo", "."));

        assertThat(ghostService.stream(PROMPT).collectList().block()).containsExactly("B", "oo", ".");
    }

    @Test
    void tellsTheUserToStartOllamaWhenItIsDown() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenThrow(new IllegalStateException("I/O error", new ConnectException("Connection refused")));

        assertThatThrownBy(() -> ghostService.seance("Anyone?"))
                .isInstanceOf(ExorcistException.class)
                .hasMessage("The spirit world is silent... Ollama is not answering at http://localhost:11434. "
                        + "Start it with: ollama serve");
    }

    @Test
    void tellsTheUserToPullAMissingModel() {
        when(chatClient.prompt().system(anyString()).user(anyString()).stream().content())
                .thenReturn(Flux.error(new RuntimeException("404 - model \"qwen2.5-coder:7b\" not found, try pulling it first")));

        assertThatThrownBy(() -> ghostService.stream(PROMPT).blockLast())
                .isInstanceOf(ExorcistException.class)
                .hasMessage("The spirit 'qwen2.5-coder:7b' has never been summoned to this machine. "
                        + "Run: ollama pull qwen2.5-coder:7b");
    }

    @Test
    void wrapsAnyOtherFailure() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenThrow(new RuntimeException("context length exceeded"));

        assertThatThrownBy(() -> ghostService.ask(PROMPT))
                .isInstanceOf(ExorcistException.class)
                .hasMessage("The spirit refused to answer: context length exceeded");
    }
}
