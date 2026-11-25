package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.ContactDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/** The practice's side of client contact: answering queries and asking clients for documents. Nothing is deleted. */
@Service
@RequiredArgsConstructor
public class ComplianceContactService {

    private final CompliancePortalQueryRepository queries;
    private final CompliancePortalMessageRepository messages;
    private final ComplianceDocumentRequestRepository requests;
    private final ComplianceClientRepository clients;

    @Transactional(readOnly = true)
    public List<StaffQuery> list(TenantId tenantId, String status) {
        Map<UUID, String> names = names(tenantId);
        List<StaffQuery> out = new ArrayList<>();
        for (CompliancePortalQuery q : queries.findAllForTenant(tenantId)) {
            if (status != null && !status.isBlank() && !status.equalsIgnoreCase(q.getStatus())) continue;
            out.add(view(q, names.get(q.getClientId())));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<StaffQuery> forClient(TenantId tenantId, UUID clientId) {
        String name = clientName(tenantId, clientId);
        List<StaffQuery> out = new ArrayList<>();
        for (CompliancePortalQuery q : queries.findForClient(tenantId, clientId)) out.add(view(q, name));
        return out;
    }

    @Transactional(readOnly = true)
    public StaffQueryDetail get(TenantId tenantId, UUID id) {
        return detail(tenantId, query(tenantId, id));
    }

    @Transactional
    public StaffQueryDetail reply(TenantId tenantId, UUID id, String body, UUID by) {
        CompliancePortalQuery q = query(tenantId, id);
        CompliancePortalMessage m = guard(() -> CompliancePortalMessage.of(tenantId, id, "STAFF", by, null, body));
        guard(() -> { q.messageAdded("STAFF"); return null; });
        messages.save(m);
        queries.save(q);
        return detail(tenantId, q);
    }

    @Transactional
    public StaffQueryDetail close(TenantId tenantId, UUID id, UUID by) {
        CompliancePortalQuery q = query(tenantId, id);
        guard(() -> { q.close(by); return null; });
        return detail(tenantId, queries.save(q));
    }

    // ---- document requests ----

    @Transactional(readOnly = true)
    public List<StaffRequest> requestsFor(TenantId tenantId, UUID clientId, LocalDate today) {
        String name = clientName(tenantId, clientId);
        List<StaffRequest> out = new ArrayList<>();
        for (ComplianceDocumentRequest r : requests.findForClient(tenantId, clientId)) out.add(view(r, name, today));
        return out;
    }

    @Transactional
    public StaffRequest ask(TenantId tenantId, UUID clientId, DocumentRequestInput in, UUID by, LocalDate today) {
        String name = clientName(tenantId, clientId);
        ComplianceDocumentRequest r = guard(() -> ComplianceDocumentRequest.create(tenantId, clientId, in.title(), in.description(), in.documentType(), in.dueDate(), today, by));
        return view(requests.save(r), name, today);
    }

    @Transactional
    public StaffRequest closeRequest(TenantId tenantId, UUID id, String note, UUID by, LocalDate today) {
        ComplianceDocumentRequest r = request(tenantId, id);
        guard(() -> { r.close(by, note); return null; });
        return view(requests.save(r), clientName(tenantId, r.getClientId()), today);
    }

    @Transactional
    public StaffRequest cancelRequest(TenantId tenantId, UUID id, String note, UUID by, LocalDate today) {
        ComplianceDocumentRequest r = request(tenantId, id);
        guard(() -> { r.cancel(by, note); return null; });
        return view(requests.save(r), clientName(tenantId, r.getClientId()), today);
    }

    // ---- internals ----

    private StaffQueryDetail detail(TenantId tenantId, CompliancePortalQuery q) {
        List<StaffMessage> out = new ArrayList<>();
        for (CompliancePortalMessage m : messages.findForQuery(tenantId, q.getId())) out.add(new StaffMessage(m.getAuthorType(), m.getAuthorId(), m.getAuthorName(), m.getBody(), m.getCreatedAt()));
        return new StaffQueryDetail(view(q, clientName(tenantId, q.getClientId())), out);
    }

    private static StaffQuery view(CompliancePortalQuery q, String clientName) {
        return new StaffQuery(q.getId(), q.getClientId(), clientName, q.getSubject(), q.getStatus(), q.getRaisedByName(), q.getCreatedAt(), q.getLastMessageAt(), q.getMessageCount());
    }

    private static StaffRequest view(ComplianceDocumentRequest r, String clientName, LocalDate today) {
        boolean late = r.isLive() && r.getDueDate() != null && r.getDueDate().isBefore(today);
        return new StaffRequest(r.getId(), r.getClientId(), clientName, r.getTitle(), r.getDescription(), r.getDocumentType(), r.getDueDate(), r.getStatus(), r.getRequestedAt(),
                r.getClosedAt(), r.getCloseNote(), late);
    }

    private Map<UUID, String> names(TenantId tenantId) {
        Map<UUID, String> out = new HashMap<>();
        for (ComplianceClient c : clients.findAll(tenantId, null, Pageable.unpaged()).getContent()) out.put(c.getId(), c.getName());
        return out;
    }

    private String clientName(TenantId tenantId, UUID clientId) {
        return clients.findByIdForTenant(tenantId, clientId).map(ComplianceClient::getName).orElseThrow(() -> new ResourceNotFoundException("ComplianceClient", clientId.toString()));
    }

    private CompliancePortalQuery query(TenantId tenantId, UUID id) {
        return queries.findOne(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("CompliancePortalQuery", id.toString()));
    }

    private ComplianceDocumentRequest request(TenantId tenantId, UUID id) {
        return requests.findOne(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("ComplianceDocumentRequest", id.toString()));
    }

    static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "CONTACT_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "CONTACT_INVALID");
        }
    }
}
