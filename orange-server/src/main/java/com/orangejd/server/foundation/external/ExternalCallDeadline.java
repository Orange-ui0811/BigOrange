package com.orangejd.server.foundation.external;

import java.time.Instant;

/** Worker-local deadline. Open explicitly on the worker; always remove it before pool reuse. */
public final class ExternalCallDeadline implements AutoCloseable {
    private static final ThreadLocal<Instant> CURRENT = new ThreadLocal<>();
    private final Instant previous;
    private ExternalCallDeadline(Instant deadline) {
        previous = CURRENT.get();
        CURRENT.set(previous != null && previous.isBefore(deadline) ? previous : deadline);
    }
    public static ExternalCallDeadline open(Instant deadline) { return new ExternalCallDeadline(deadline); }
    public static Instant bound(Instant deadline) {
        Instant current = CURRENT.get();
        return current != null && current.isBefore(deadline) ? current : deadline;
    }
    @Override public void close() { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
}
