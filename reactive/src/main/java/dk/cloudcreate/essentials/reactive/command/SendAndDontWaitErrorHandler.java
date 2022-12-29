package dk.cloudcreate.essentials.reactive.command;

import dk.cloudcreate.essentials.shared.Exceptions;
import org.slf4j.*;

import java.time.Duration;

import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

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

    /**
     * Fallback {@link SendAndDontWaitErrorHandler} that only error logs any issues.<br>
     * Note: If the {@link FallbackSendAndDontWaitErrorHandler} is used with a Durable Command Bus (e.g. using DurableQueues),
     * then any failing command will not be retried.<br>
     * Instead use {@link RethrowingSendAndDontWaitErrorHandler}
     */
    class FallbackSendAndDontWaitErrorHandler implements SendAndDontWaitErrorHandler {
        private static final Logger log = LoggerFactory.getLogger(FallbackSendAndDontWaitErrorHandler.class);

        @Override
        public void handleError(Exception exception, Object command, CommandHandler commandHandler) {
            log.error(msg("SendAndDontWait ERROR: {} '{}' failed to handle command: {}",
                          CommandHandler.class.getSimpleName(),
                          commandHandler.getClass().getName(),
                          command), exception);
        }
    }

    /**
     * Fallback {@link SendAndDontWaitErrorHandler} that error logs any issues and rethrows the exception.<br>
     * The {@link RethrowingSendAndDontWaitErrorHandler} is compatible with a Durable Command Bus (e.g. using DurableQueues),
     * as rethrowing the exceptions allows the command to be retried
     */
    class RethrowingSendAndDontWaitErrorHandler implements SendAndDontWaitErrorHandler {
        private static final Logger log = LoggerFactory.getLogger(FallbackSendAndDontWaitErrorHandler.class);

        @Override
        public void handleError(Exception exception, Object command, CommandHandler commandHandler) {
            log.error(msg("SendAndDontWait ERROR: {} '{}' failed to handle command: {}",
                          CommandHandler.class.getSimpleName(),
                          commandHandler.getClass().getName(),
                          command), exception);
            Exceptions.sneakyThrow(exception);
        }
    }
}
