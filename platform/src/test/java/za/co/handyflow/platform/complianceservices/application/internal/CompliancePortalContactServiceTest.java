package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.application.internal.CompliancePortalDataService.Scope;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.ContactDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalUserRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompliancePortalContactServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID OTHER_CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final UUID PERSON = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final Scope SCOPE = new Scope(TENANT, CLIENT);

    @Mock CompliancePortalDataService data;
    @Mock CompliancePortalQueryRepository queries;
    @Mock CompliancePortalMessageRepository messages;
    @Mock ComplianceDocumentRequestRepository requests;
    @Mock PortalUserRepository portalUsers;
    @Mock ClientComplianceDocumentService documents;
    @Mock ComplianceContactNotifier notifier;
    @Mock ComplianceClientRepository clients;
    @InjectMocks CompliancePortalContactService service;

    @Test
    void raiseRefusedWhenFiveAreOpen() {
        when(data.resolveScope(PERSON, CLIENT)).thenReturn(SCOPE);
        when(queries.countOpenFor(TENANT, CLIENT, PERSON)).thenReturn(5);
        assertThatThrownBy(() -> service.raise(PERSON, CLIENT, "S", "B")).isInstanceOf(HandyFlowException.class);
        verify(queries, never()).save(any());
    }

    @Test
    void raiseSavesQueryAndFirstMessage() {
        when(data.resolveScope(PERSON, CLIENT)).thenReturn(SCOPE);
        when(queries.countOpenFor(TENANT, CLIENT, PERSON)).thenReturn(0);
        when(messages.findForQuery(any(), any())).thenReturn(List.of());
        PortalQueryDetail d = service.raise(PERSON, CLIENT, "Subject", "Body");
        assertThat(d.query().subject().equals("Subject")).isTrue();
        verify(queries).save(any(CompliancePortalQuery.class));
        verify(messages).save(any(CompliancePortalMessage.class));
        verify(notifier).clientWrote(eq(TENANT), any(CompliancePortalQuery.class), any(), eq(true));
    }

    @Test
    void queryOfAnotherClientIsNotFound() {
        CompliancePortalQuery q = CompliancePortalQuery.raise(TENANT, OTHER_CLIENT, "S", PERSON, "Pat", 0);
        when(data.scopesOf(PERSON)).thenReturn(List.of(SCOPE));
        when(queries.findOne(TENANT, q.getId())).thenReturn(Optional.of(q));
        assertThatThrownBy(() -> service.get(PERSON, q.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void staffMessagesAreShownAsThePractice() {
        CompliancePortalQuery q = CompliancePortalQuery.raise(TENANT, CLIENT, "S", PERSON, "Pat", 0);
        when(data.scopesOf(PERSON)).thenReturn(List.of(SCOPE));
        when(queries.findOne(TENANT, q.getId())).thenReturn(Optional.of(q));
        when(messages.findForQuery(TENANT, q.getId())).thenReturn(List.of(
                CompliancePortalMessage.of(TENANT, q.getId(), "STAFF", UUID.randomUUID(), null, "Answer")));
        PortalMessage m = service.get(PERSON, q.getId()).messages().get(0);
        assertThat(m.fromClient()).isFalse();
        assertThat(m.author().equals(CompliancePortalContactService.PRACTICE)).isTrue();
    }

    @Test
    void badFileTypeRefusedBeforeAnythingIsStored() {
        org.springframework.mock.web.MockMultipartFile f = new org.springframework.mock.web.MockMultipartFile("file", "run.exe", "application/octet-stream", new byte[]{1});
        assertThatThrownBy(() -> service.upload(PERSON, UUID.randomUUID(), f, java.time.LocalDate.of(2026, 11, 25))).isInstanceOf(HandyFlowException.class);
        verifyNoInteractions(documents);
    }

    @Test
    void requestOfAnotherClientIsNotFound() {
        ComplianceDocumentRequest r = ComplianceDocumentRequest.create(TENANT, OTHER_CLIENT, "T", null, null, null, java.time.LocalDate.of(2026, 11, 25), PERSON);
        org.springframework.mock.web.MockMultipartFile f = new org.springframework.mock.web.MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1});
        when(data.scopesOf(PERSON)).thenReturn(List.of(SCOPE));
        when(requests.findOne(TENANT, r.getId())).thenReturn(Optional.of(r));
        assertThatThrownBy(() -> service.upload(PERSON, r.getId(), f, java.time.LocalDate.of(2026, 11, 25))).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(documents);
    }
}
