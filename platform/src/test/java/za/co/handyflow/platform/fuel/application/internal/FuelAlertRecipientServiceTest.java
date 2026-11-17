package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.FuelAlertRecipient;
import za.co.handyflow.platform.fuel.domain.repository.FuelAlertRecipientRepository;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Written, not compiled or run. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelAlertRecipientServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    @Mock FuelAlertRecipientRepository repo;
    @Mock TenantAdminRecipients admins;
    @InjectMocks FuelAlertRecipientService service;

    final Recipient admin = Recipient.user(UUID.randomUUID(), "Admin", "a@x.co", null);
    final Recipient other = Recipient.user(UUID.randomUUID(), "Other", "o@x.co", null);

    @Test @DisplayName("with nobody added, the administrators are told, as before")
    void nobodyAddedMeansAdminsOnly() {
        when(admins.resolveTenantAdmins(T)).thenReturn(List.of(admin));
        when(repo.findByTopic(T, "VARIANCE")).thenReturn(List.of());
        assertEquals(List.of(admin), service.recipientsFor(T, "VARIANCE"));
        verify(admins, never()).resolveUsers(any(), any());
    }

    @Test @DisplayName("added people are told as well, and a person who is also an administrator is told once")
    void addsAndDedupes() {
        when(admins.resolveTenantAdmins(T)).thenReturn(List.of(admin));
        when(repo.findByTopic(T, "VARIANCE")).thenReturn(List.of(
                FuelAlertRecipient.of(T, "VARIANCE", admin.userId(), null), FuelAlertRecipient.of(T, "VARIANCE", other.userId(), null)));
        when(admins.resolveUsers(eq(T), any())).thenReturn(List.of(admin, other));
        assertEquals(List.of(admin, other), service.recipientsFor(T, "VARIANCE"));
    }

    @Test @DisplayName("a lookup failure falls back to the administrators")
    void failureFallsBack() {
        when(admins.resolveTenantAdmins(T)).thenReturn(List.of(admin));
        when(repo.findByTopic(T, "VARIANCE")).thenThrow(new RuntimeException("db"));
        assertEquals(List.of(admin), service.recipientsFor(T, "VARIANCE"));
    }

    @Test @DisplayName("saving adds the new people and removes those no longer chosen")
    void replaceAddsAndRemoves() {
        UUID keep = UUID.randomUUID(), drop = UUID.randomUUID(), add = UUID.randomUUID();
        FuelAlertRecipient keepRow = FuelAlertRecipient.of(T, "VARIANCE", keep, null), dropRow = FuelAlertRecipient.of(T, "VARIANCE", drop, null);
        when(repo.findByTopic(T, "VARIANCE")).thenReturn(List.of(keepRow, dropRow));
        when(admins.resolveUsers(eq(T), any())).thenReturn(List.of(Recipient.user(keep, "K", "k@x.co", null), Recipient.user(add, "A", "a2@x.co", null)));
        service.replace(T, "VARIANCE", List.of(keep, add), UUID.randomUUID());
        verify(repo).delete(dropRow);
        verify(repo, never()).delete(keepRow);
        ArgumentCaptor<FuelAlertRecipient> saved = ArgumentCaptor.forClass(FuelAlertRecipient.class);
        verify(repo, times(1)).save(saved.capture());
        assertEquals(add, saved.getValue().getUserId());
    }

    @Test @DisplayName("a person who is not an active user of this company is refused and nothing changes")
    void refusesUnknownUser() {
        UUID stranger = UUID.randomUUID();
        when(admins.resolveUsers(eq(T), any())).thenReturn(List.of());
        assertThrows(HandyFlowException.class, () -> service.replace(T, "VARIANCE", List.of(stranger), null));
        verify(repo, never()).save(any());
        verify(repo, never()).delete(any());
    }

    @Test @DisplayName("an empty list clears the extras; an unknown alert type and too many people are refused")
    void clearsAndValidates() {
        FuelAlertRecipient row = FuelAlertRecipient.of(T, "VARIANCE", UUID.randomUUID(), null);
        when(repo.findByTopic(T, "VARIANCE")).thenReturn(List.of(row));
        service.replace(T, "VARIANCE", List.of(), null);
        verify(repo).delete(row);
        assertThrows(IllegalArgumentException.class, () -> service.replace(T, "NOPE", List.of(), null));
        List<UUID> eleven = java.util.stream.IntStream.range(0, 11).mapToObj(i -> UUID.randomUUID()).toList();
        assertThrows(IllegalArgumentException.class, () -> service.replace(T, "VARIANCE", eleven, null));
    }
}
