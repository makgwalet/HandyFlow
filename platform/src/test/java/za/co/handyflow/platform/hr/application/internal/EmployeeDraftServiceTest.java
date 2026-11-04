package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.HrEmployeeDraft;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeDraftRepository;
import za.co.handyflow.platform.hr.dto.EmployeeDraftDtos.Save;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmployeeDraftServiceTest {

    private HrEmployeeDraftRepository repo;
    private EmployeeDraftService service;
    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID me = UUID.randomUUID(), someoneElse = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repo = mock(HrEmployeeDraftRepository.class);
        service = new EmployeeDraftService(repo);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(repo.mine(any(), any())).thenReturn(List.of());
    }

    private static Save save(String first) { return new Save("{\"firstName\":\"" + first + "\"}", 2, first, "Nkosi"); }

    @Test
    void startsADraftLabelledWithTheName() {
        var d = service.save(tenant, me, null, save("Thandi"));
        assertThat(d.label()).isEqualTo("Thandi Nkosi");
        assertThat(d.step()).isEqualTo(2);
    }

    @Test
    void replacesOnlyMyOwnDraft() {
        HrEmployeeDraft mine = HrEmployeeDraft.create(tenant, me, "Old", 0, "{}");
        when(repo.mineById(tenant, me, mine.getId())).thenReturn(Optional.of(mine));
        when(repo.mineById(tenant, someoneElse, mine.getId())).thenReturn(Optional.empty());
        assertThat(service.save(tenant, me, mine.getId(), save("Sipho")).label()).isEqualTo("Sipho Nkosi");
        assertThatThrownBy(() -> service.save(tenant, someoneElse, mine.getId(), save("X"))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.get(tenant, someoneElse, mine.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(tenant, someoneElse, mine.getId())).isInstanceOf(ResourceNotFoundException.class);
        verify(repo, never()).delete(any());
    }

    @Test
    void refusesAnElevenththDraftAndAnUnidentifiedCaller() {
        List<HrEmployeeDraft> ten = new ArrayList<>();
        for (int i = 0; i < 10; i++) ten.add(HrEmployeeDraft.create(tenant, me, "d" + i, 0, "{}"));
        when(repo.mine(tenant, me)).thenReturn(ten);
        assertThatThrownBy(() -> service.save(tenant, me, null, save("A"))).isInstanceOf(IllegalStateException.class).hasMessageContaining("10 drafts");
        assertThatThrownBy(() -> service.save(tenant, null, null, save("A"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listingClearsOldDraftsAndCarriesNoContents() {
        HrEmployeeDraft d = HrEmployeeDraft.create(tenant, me, "Thandi Nkosi", 1, "{\"idNumber\":\"secret\"}");
        when(repo.mine(tenant, me)).thenReturn(List.of(d));
        var out = service.list(tenant, me);
        verify(repo).deleteOlderThan(any(), any());
        assertThat(out).hasSize(1);
        assertThat(out.get(0).toString()).doesNotContain("secret");
    }
}
