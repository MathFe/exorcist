package dev.exorcist.shell;

import dev.exorcist.ExorcistException;
import org.jline.terminal.Terminal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.shell.command.CommandExceptionResolver;
import org.springframework.shell.command.CommandHandlingResult;
import org.springframework.shell.jline.PromptProvider;

@Configuration
class ShellConfig {

    /** Shows {@link ExorcistException}s as a friendly message instead of a stack trace. */
    @Bean
    CommandExceptionResolver exorcistExceptionResolver(Renderer renderer) {
        return error -> error instanceof ExorcistException
                ? CommandHandlingResult.of(renderer.error(error.getMessage()) + System.lineSeparator())
                : null;
    }

    /** {@code exorcist:>}, or {@code exorcist[Assert]:>} while a class is possessed. */
    @Bean
    PromptProvider exorcistPrompt(ExorcistSession session, Style style) {
        return () -> style.prompt(session.possession()
                .map(p -> "exorcist[" + p.parsed().name() + "]:> ")
                .orElse("exorcist:> "));
    }

    /** The banner, only for the interactive shell: scripted runs (with arguments) stay clean. */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    ApplicationRunner banner(Renderer renderer, Terminal terminal,
                             @Value("${spring.ai.ollama.chat.options.model}") String model) {
        return args -> {
            if (args.getSourceArgs().length == 0) {
                terminal.writer().println(renderer.banner(model));
                terminal.writer().flush();
            }
        };
    }
}
