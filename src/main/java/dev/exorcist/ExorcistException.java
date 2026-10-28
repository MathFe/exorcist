package dev.exorcist;

/**
 * A failure the user can understand and fix (Ollama down, not a Git repo, class not found...).
 * Its message is shown as-is in the shell, without a stack trace.
 */
public class ExorcistException extends RuntimeException {

    public ExorcistException(String message) {
        super(message);
    }

    public ExorcistException(String message, Throwable cause) {
        super(message, cause);
    }
}
