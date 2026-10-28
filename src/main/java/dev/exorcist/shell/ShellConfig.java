package dev.exorcist.shell;

import dev.exorcist.ExorcistException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.command.CommandExceptionResolver;
import org.springframework.shell.command.CommandHandlingResult;

@Configuration
class ShellConfig {

    /** Shows {@link ExorcistException}s as a friendly message instead of a stack trace. */
    @Bean
    CommandExceptionResolver exorcistExceptionResolver() {
        return error -> error instanceof ExorcistException
                ? CommandHandlingResult.of(error.getMessage() + System.lineSeparator())
                : null;
    }
}
