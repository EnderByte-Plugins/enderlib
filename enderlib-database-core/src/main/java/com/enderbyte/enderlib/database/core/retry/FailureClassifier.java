package com.enderbyte.enderlib.database.core.retry;

/**
 * Decides whether a failure is worth retrying.
 *
 * <p>The distinction is the whole point of the retry system: retrying a lost connection is
 * correct, retrying a constraint violation or a syntax error just multiplies the damage and hides
 * the bug.
 */
@FunctionalInterface
public interface FailureClassifier {

    /**
     * @param failure the exception that ended the attempt
     * @return {@code true} if another attempt could plausibly succeed
     */
    boolean isTransient(Throwable failure);
}
