package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.warehousing.domain.model.WhseProfile;
import za.co.handyflow.platform.warehousing.domain.repository.WhseProfileRepository;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WhsePutawaySyncSchedulerTest {

    @Test
    void oneTenantFailingDoesNotStopTheOthers() {
        WhseProfileRepository profiles = mock(WhseProfileRepository.class);
        WhseTaskService tasks = mock(WhseTaskService.class);
        WhseProfile a = mock(WhseProfile.class);
        WhseProfile b = mock(WhseProfile.class);
        UUID ta = UUID.randomUUID();
        UUID tb = UUID.randomUUID();
        when(a.getTenantId()).thenReturn(ta);
        when(b.getTenantId()).thenReturn(tb);
        when(profiles.findAll()).thenReturn(List.of(a, b));
        when(tasks.syncPutaway(any())).thenThrow(new IllegalStateException("boom")).thenReturn(2);

        new WhsePutawaySyncScheduler(profiles, tasks).syncAll();

        verify(tasks, times(2)).syncPutaway(any());
    }
}
