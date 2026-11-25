package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.application.internal.CompliancePortalDataService.Scope;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.ContactDtos.*;
import za.co.handyflow.platform.shared.PortalUser;
import za.co.handyflow.platform.shared.PortalUserRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static za.co.handyflow.platform.complianceservices.application.internal.ComplianceContactService.guard;

/**
 * A client contact's side of contact with the practice. The tenant and client always come from the caller's own grants, never from an id they
 * were given, and a query that belongs to a client they hold no grant for is reported as not found. Staff are shown only as "The practice".
 */
@Service
@RequiredArgsConstructor
public class CompliancePortalContactService {

    public static final String PRACTICE = "The practice";

    private final CompliancePortalDataService data;
    private final CompliancePortalQueryRepository queries;
    private final CompliancePortalMessageRepository messages;
    private final ComplianceDocumentRequestRepository requests;
    private final PortalUserRepository portalUsers;

    @Transactional(readOnly = true)
    public List<PortalQuery> list(UUID portalUserId, UUID clientId) {
        Scope s = data.resolveScope(portalUserId, clientId);
        List<PortalQuery> out = new ArrayList<>();
        for (CompliancePortalQuery q : queries.findForClient(s.tenantId(), s.clientId())) out.add(view(q));
        return out;
    }

    @Transactional(readOnly = true)
    public PortalQueryDetail get(UUID portalUserId, UUID queryId) {
        Found f = find(portalUserId, queryId);
        return detail(f.scope(), f.query());
    }

    @Transactional
    public PortalQueryDetail raise(UUID portalUserId, UUID clientId, String subject, String body) {
        Scope s = data.resolveScope(portalUserId, clientId);
        String name = portalUsers.findById(portalUserId).map(PortalUser::getFullName).orElse(null);
        int open = queries.countOpenFor(s.tenantId(), s.clientId(), portalUserId);
        CompliancePortalQuery q = guard(() -> CompliancePortalQuery.raise(s.tenantId(), s.clientId(), subject, portalUserId, name, open));
        CompliancePortalMessage m = guard(() -> CompliancePortalMessage.of(s.tenantId(), q.getId(), "CLIENT", portalUserId, name, body));
        guard(() -> { q.messageAdded("CLIENT"); return null; });
        queries.save(q);
        messages.save(m);
        return detail(s, q);
    }

    @Transactional
    public PortalQueryDetail reply(UUID portalUserId, UUID queryId, String body) {
        Found f = find(portalUserId, queryId);
        String name = portalUsers.findById(portalUserId).map(PortalUser::getFullName).orElse(null);
        CompliancePortalMessage m = guard(() -> CompliancePortalMessage.of(f.scope().tenantId(), queryId, "CLIENT", portalUserId, name, body));
        guard(() -> { f.query().messageAdded("CLIENT"); return null; });
        messages.save(m);
        queries.save(f.query());
        return detail(f.scope(), f.query());
    }

    /** What the practice has asked this client for and has not yet closed. */
    @Transactional(readOnly = true)
    public List<PortalRequest> requests(UUID portalUserId, UUID clientId, LocalDate today) {
        Scope s = data.resolveScope(portalUserId, clientId);
        List<PortalRequest> out = new ArrayList<>();
        for (ComplianceDocumentRequest r : requests.findForClient(s.tenantId(), s.clientId())) {
            if (!r.isLive()) continue;
            out.add(new PortalRequest(r.getId(), r.getTitle(), r.getDescription(), r.getDocumentType(), r.getDueDate(), r.getStatus(), r.getRequestedAt(),
                    r.getDueDate() != null && r.getDueDate().isBefore(today)));
        }
        return out;
    }

    private record Found(Scope scope, CompliancePortalQuery query) {}

    /** Looks the query up only among the clients this account holds a grant for. */
    private Found find(UUID portalUserId, UUID queryId) {
        for (Scope s : data.scopesOf(portalUserId)) {
            var q = queries.findOne(s.tenantId(), queryId).filter(x -> x.getClientId().equals(s.clientId()));
            if (q.isPresent()) return new Found(s, q.get());
        }
        throw new ResourceNotFoundException("CompliancePortalQuery", queryId.toString());
    }

    private PortalQueryDetail detail(Scope s, CompliancePortalQuery q) {
        List<PortalMessage> out = new ArrayList<>();
        for (CompliancePortalMessage m : messages.findForQuery(s.tenantId(), q.getId())) {
            boolean fromClient = "CLIENT".equals(m.getAuthorType());
            out.add(new PortalMessage(fromClient, fromClient ? (m.getAuthorName() == null ? "You" : m.getAuthorName()) : PRACTICE, m.getBody(), m.getCreatedAt()));
        }
        return new PortalQueryDetail(view(q), out);
    }

    private static PortalQuery view(CompliancePortalQuery q) {
        return new PortalQuery(q.getId(), q.getSubject(), q.getStatus(), q.getCreatedAt(), q.getLastMessageAt(), q.getMessageCount());
    }
}
