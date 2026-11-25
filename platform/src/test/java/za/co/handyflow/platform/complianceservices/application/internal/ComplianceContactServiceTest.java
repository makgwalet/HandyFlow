package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.ContactDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplianceContactServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID PERSON = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Mock CompliancePortalQueryRepository queries;
    @Mock CompliancePortalMessageRepository messages;
    @Mock ComplianceDocumentRequestRepository requests;
    @Mock ComplianceClientRepository clients;
    @InjectMocks ComplianceContactService service;

    private ComplianceClient client() {
        ComplianceClient c = mock(ComplianceClient.class);
        when(c.getName()).thenReturn("Acme");
        return c;
    }

    @Test
    void replyMarksAnsweredAndRecordsWhoReplied() {
        CompliancePortalQuery q = CompliancePortalQuery.raise(TENANT, CLIENT, "S", PERSON, "Pat", 0);
        q.messageAdded("CLIENT");
        when(queries.findOne(TENANT, q.getId())).thenReturn(Optional.of(q));
        when(clients.findByIdForTenant(TENANT, CLIENT)).thenReturn(Optional.of(client()));
        when(messages.findForQuery(any(), any())).thenReturn(List.of());
        StaffQueryDetail d = service.reply(TENANT, q.getId(), "Thanks", PERSON);
        assertThat(d.query().status().equals("ANSWERED")).isTrue();
        verify(messages).save(any(CompliancePortalMessage.class));
    }

    @Test
    void replyToClosedQueryIsConflict() {
        CompliancePortalQuery q = CompliancePortalQuery.raise(TENANT, CLIENT, "S", PERSON, "Pat", 0);
        q.close(PERSON);
        when(queries.findOne(TENANT, q.getId())).thenReturn(Optional.of(q));
        assertThatThrownBy(() -> service.reply(TENANT, q.getId(), "Hi", PERSON)).isInstanceOf(HandyFlowException.class);
        verify(messages, never()).save(any());
    }

    @Test
    void askSavesRequestAndPastDateRefused() {
        when(clients.findByIdForTenant(TENANT, CLIENT)).thenReturn(Optional.of(client()));
        when(requests.save(any(ComplianceDocumentRequest.class))).thenAnswer(i -> i.getArgument(0));
        StaffRequest r = service.ask(TENANT, CLIENT, new DocumentRequestInput("Bank statements", null, null, TODAY.plusDays(3)), PERSON, TODAY);
        assertThat(r.status().equals("OPEN")).isTrue();
        assertThatThrownBy(() -> service.ask(TENANT, CLIENT, new DocumentRequestInput("X", null, null, TODAY.minusDays(1)), PERSON, TODAY))
                .isInstanceOf(HandyFlowException.class);
    }

    @Test
    void closingRequestNeedsAReason() {
        ComplianceDocumentRequest r = ComplianceDocumentRequest.create(TENANT, CLIENT, "T", null, null, null, TODAY, PERSON);
        when(requests.findOne(TENANT, r.getId())).thenReturn(Optional.of(r));
        assertThatThrownBy(() -> service.closeRequest(TENANT, r.getId(), "short", PERSON, TODAY)).isInstanceOf(HandyFlowException.class);
        verify(requests, never()).save(any());
    }
}
