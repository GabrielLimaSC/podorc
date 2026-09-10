package com.podorc.runtime.spi;

import java.util.function.Supplier;

/**
 * Runs a unit of work at most once per {@code message_id}, atomically with recording that the
 * message was processed.
 *
 * <p>Port only. The JDBC implementation and its final contract are owned by <strong>S1-04</strong>
 * (Dev 1); this interface is defined in {@code agent-runtime} so S1-05 and the SPI package can land
 * first. If S1-04 needs to adjust the signature, this is the place.
 *
 * <p>Semantics the implementation must provide:
 * <ul>
 *   <li>the first call for a given {@code messageId} runs {@code work}; its side effects and the
 *       {@code processed_message} marker commit in one transaction;</li>
 *   <li>a later call with the same {@code messageId} returns {@link Outcome#duplicate()} without
 *       running {@code work} — no duplicate LLM call, no duplicate effect;</li>
 *   <li>if {@code work} throws, the transaction rolls back and no marker is left behind, so a retry
 *       can run it again.</li>
 * </ul>
 */
@FunctionalInterface
public interface IdempotencyGuard {

    <T> Outcome<T> runOnce(String messageId, Supplier<T> work);

    /**
     * @param executed {@code true} if {@code work} ran on this call, {@code false} if the message was
     *                 already processed
     * @param result   the value returned by {@code work}, or {@code null} when {@code executed} is
     *                 {@code false}
     */
    record Outcome<T>(boolean executed, T result) {

        public static <T> Outcome<T> executed(T result) {
            return new Outcome<>(true, result);
        }

        public static <T> Outcome<T> duplicate() {
            return new Outcome<>(false, null);
        }
    }
}
