package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmSyncOpRepository;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FmSyncRetentionJobTest {
    @Test
    void purgesRecordsOlderThanNinetyDays() {
        List<Instant> cutoffs = new ArrayList<>();
        FmSyncOpRepository repo = (FmSyncOpRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{FmSyncOpRepository.class}, (p, m, a) -> { cutoffs.add((Instant) a[0]); return 7; });
        Instant now = Instant.parse("2026-10-27T10:00:00Z");
        assertEquals(7, new FmSyncRetentionJob(repo, null).purge(now));
        assertEquals(now.minus(Duration.ofDays(90)), cutoffs.get(0));
    }
}
