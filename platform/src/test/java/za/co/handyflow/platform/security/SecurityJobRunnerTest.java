package za.co.handyflow.platform.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import za.co.handyflow.platform.security.application.internal.SecurityJobRunner;
import za.co.handyflow.platform.shared.SchedulerLease;

class SecurityJobRunnerTest {

    @Test void withoutARunnerJobsJustRun() {
        AtomicInteger n = new AtomicInteger();
        SecurityJobRunner.exclusively(null, "x", Duration.ofMinutes(1), n::incrementAndGet);
        SecurityJobRunner.tx(null, true, n::incrementAndGet);
        assertEquals(2, n.get());
        assertEquals("v", SecurityJobRunner.txGet(null, false, () -> "v"));
    }

    @Test void exclusivelyDelegatesToTheLease() {
        SecurityJobRunner runner = new SecurityJobRunner();
        SchedulerLease lease = mock(SchedulerLease.class);
        ReflectionTestUtils.setField(runner, "lease", lease);
        Runnable job = () -> {};
        SecurityJobRunner.exclusively(runner, "security-x", Duration.ofMinutes(4), job);
        verify(lease).runExclusively("security-x", Duration.ofMinutes(4), job);
    }

    @Test void txRunsInsideATransactionAndCommits() {
        SecurityJobRunner runner = new SecurityJobRunner();
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        ReflectionTestUtils.setField(runner, "txManager", tm);
        AtomicInteger n = new AtomicInteger();
        SecurityJobRunner.tx(runner, true, n::incrementAndGet);
        assertEquals(1, n.get());
        verify(tm).getTransaction(argThat(d -> d.isReadOnly()));
        verify(tm).commit(any());
    }

    @Test void txRollsBackWhenTheWorkThrows() {
        SecurityJobRunner runner = new SecurityJobRunner();
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        ReflectionTestUtils.setField(runner, "txManager", tm);
        assertThrows(IllegalStateException.class, () -> SecurityJobRunner.tx(runner, false, () -> { throw new IllegalStateException("boom"); }));
        verify(tm).rollback(any());
        verify(tm, never()).commit(any());
    }
}
