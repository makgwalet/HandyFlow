package za.co.handyflow.platform.security.application.internal;

import java.time.Duration;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.shared.SchedulerLease;

/**
 * What every Security scheduled job needs, in one place:
 * <ul>
 *   <li>{@link #exclusively}: with two or more app instances running, only one of them runs a given job at a time
 *       (the platform's database lease), so alerts and sweeps are not sent twice.</li>
 *   <li>{@link #tx}: each tenant's work runs in its own transaction. A scheduler calling its own
 *       {@code @Transactional} method gets no transaction at all (Spring only wraps calls that come in through the
 *       proxy), so the per-tenant method annotations did nothing; this does what they were meant to.</li>
 * </ul>
 * Both static helpers accept a null runner and then simply run the work directly, so classes constructed by hand in
 * unit tests keep working without it.
 */
@Slf4j
@Component
public class SecurityJobRunner {

    @Autowired(required = false)
    private SchedulerLease lease;

    @Autowired(required = false)
    private PlatformTransactionManager txManager;

    /** Runs {@code job} unless another instance is already running {@code lockName}. */
    public static void exclusively(SecurityJobRunner runner, String lockName, Duration maxHold, Runnable job) {
        if (runner == null || runner.lease == null) { job.run(); return; }
        runner.lease.runExclusively(lockName, maxHold, job);
    }

    /** Runs {@code work} in its own transaction (read-only when asked). */
    public static void tx(SecurityJobRunner runner, boolean readOnly, Runnable work) {
        txGet(runner, readOnly, () -> { work.run(); return null; });
    }

    public static <T> T txGet(SecurityJobRunner runner, boolean readOnly, Supplier<T> work) {
        if (runner == null || runner.txManager == null) return work.get();
        TransactionTemplate template = new TransactionTemplate(runner.txManager);
        template.setReadOnly(readOnly);
        return template.execute(status -> work.get());
    }
}
