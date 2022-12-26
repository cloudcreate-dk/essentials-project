package dk.cloudcreate.essentials.reactive.command;

import java.time.Duration;

/**
 * Exception handler that will handle errors that occur during {@link CommandBus#sendAndDontWait(Object)}/{@link CommandBus#sendAndDontWait(Object, Duration)}
 */
public interface SendAndDontWaitErrorHandler {
    /**
     * Handle an exception that occurred during {@link CommandBus#sendAndDontWait(Object)}/{@link CommandBus#sendAndDontWait(Object, Duration)}
     *
     * @param exception      the exception that occurred during {@link CommandBus#sendAndDontWait(Object)}/{@link CommandBus#sendAndDontWait(Object, Duration)}
     * @param command        the command that caused the exception
     * @param commandHandler the command handler that can handle the command
     */
    void handleError(Exception exception, Object command, CommandHandler commandHandler);
}
