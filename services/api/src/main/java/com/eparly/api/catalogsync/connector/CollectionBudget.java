package com.eparly.api.catalogsync.connector;

/** Limites cooperatives d'une execution, verifiees entre deux requetes. */
public final class CollectionBudget {
    private final long deadlineNanos;
    private final java.util.function.BooleanSupplier stopping;

    public CollectionBudget(long deadlineNanos, java.util.function.BooleanSupplier stopping) {
        this.deadlineNanos = deadlineNanos;
        this.stopping = stopping;
    }

    public boolean deadlineReached() {
        return System.nanoTime() - deadlineNanos >= 0;
    }

    public boolean stopping() {
        return stopping.getAsBoolean();
    }

    public void ensureBeforeDeadline() {
        if (deadlineReached()) throw new RunDeadlineExceededException();
    }

    public static final class RunDeadlineExceededException extends RuntimeException {
        public RunDeadlineExceededException() {
            super("Maximum catalog sync execution duration reached");
        }
    }
}
