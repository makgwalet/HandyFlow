package za.co.handyflow.platform.accountant.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accountant.domain.model.AccClient;
import za.co.handyflow.platform.accountant.domain.model.AccDocumentRequest;
import za.co.handyflow.platform.accountant.domain.model.AccountantProfile;
import za.co.handyflow.platform.accountant.domain.repository.AccClientRepository;
import za.co.handyflow.platform.accountant.domain.repository.AccDocumentRequestRepository;
import za.co.handyflow.platform.accountant.domain.repository.AccountantProfileRepository;
import za.co.handyflow.platform.accountant.dto.CreateDocumentRequestRequest;
import za.co.handyflow.platform.accountant.dto.DocumentRequestResponse;
import za.co.handyflow.platform.accountant.dto.UpdateDocumentRequestStatusRequest;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Closes the accountant module audit's "document requests" gap.
 * Deliberately a separate service, not folded into AccWorkpaperService
 * or AccountantService — matches the precedent already set this
 * session rather than letting any one class keep growing.
 * <p>
 * FIX: AccDocumentRequest.items is now stored as a plain String (raw
 * JSON text), not a Hibernate-native JSON-typed List — see that
 * entity's own class Javadoc for why. Serialization to/from
 * List&lt;String&gt; happens here, at the service boundary, using a
 * standard Jackson ObjectMapper (Spring Boot's own auto-configured
 * bean, not a new dependency).
 * <p>
 * FIX: confirmed via real testing that createRequest() was creating
 * requests with no notification to the client at all — a silent
 * database record with no way for the client to actually find out.
 * Now sends a real email via EmailService/EmailTemplates.
 * documentRequestCreated(), matching the same "skip silently if no
 * contact email, don't fail the whole operation" pattern already used
 * for every other client-facing email in this module.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccDocumentRequestService {

    private final AccClientRepository clientRepo;
    private final AccDocumentRequestRepository requestRepo;
    private final AccountantProfileRepository profileRepo;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    private AccClient findActiveClient(TenantId tenantId, UUID clientId) {
        return clientRepo.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new HandyFlowException("Client not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    @Transactional
    public DocumentRequestResponse createRequest(TenantId tenantId, UUID clientId,
                                                 CreateDocumentRequestRequest req, UUID requestedBy) {
        AccClient client = findActiveClient(tenantId, clientId);
        String itemsJson = writeItems(req.items());
        AccDocumentRequest request = AccDocumentRequest.create(tenantId.getValue(), clientId, req.folderId(),
                requestedBy, req.description(), itemsJson, req.dueDate());
        requestRepo.save(request);
        log.info("Document request created for client={}: {} item(s)", clientId, req.items().size());

        if (client.getContactEmail() != null && !client.getContactEmail().isBlank()) {
            String firmName = profileRepo.findByTenantId(tenantId)
                    .map(AccountantProfile::getFirmName)
                    .orElse("your accountant");
            String dueDateStr = req.dueDate() != null ? req.dueDate().toString() : null;
            emailService.send(client.getContactEmail(),
                    "Document request from " + firmName,
                    EmailTemplates.documentRequestCreated(
                            client.getTradingName(), firmName, req.description(), req.items(), dueDateStr));
        } else {
            log.warn("Document request created for client={} but no contact email on file — no notification sent", clientId);
        }

        return toResponse(request);
    }

    @Transactional(readOnly = true)
    public List<DocumentRequestResponse> getRequests(TenantId tenantId, UUID clientId) {
        findActiveClient(tenantId, clientId);
        return requestRepo.findByTenantIdAndClientId(tenantId.getValue(), clientId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public DocumentRequestResponse updateStatus(TenantId tenantId, UUID clientId, UUID requestId,
                                                UpdateDocumentRequestStatusRequest req) {
        AccDocumentRequest request = requireOwned(tenantId, clientId, requestId);
        try {
            request.updateStatus(req.status());
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }
        requestRepo.save(request);
        return toResponse(request);
    }

    private AccDocumentRequest requireOwned(TenantId tenantId, UUID clientId, UUID requestId) {
        AccDocumentRequest request = requestRepo.findByTenantIdAndId(tenantId.getValue(), requestId)
                .orElseThrow(() -> new HandyFlowException("Document request not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!request.getClientId().equals(clientId)) {
            throw new HandyFlowException("Document request not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        return request;
    }

    private String writeItems(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new HandyFlowException("Failed to process requested items", HttpStatus.INTERNAL_SERVER_ERROR, "SERIALIZATION_ERROR");
        }
    }

    private List<String> readItems(String itemsJson) {
        if (itemsJson == null || itemsJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(itemsJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse stored items JSON: {}", itemsJson, e);
            return List.of();
        }
    }

    private DocumentRequestResponse toResponse(AccDocumentRequest r) {
        return new DocumentRequestResponse(r.getId(), r.getDescription(), readItems(r.getItemsJson()), r.getStatus(),
                r.getDueDate(), r.getCompletedAt(), r.getCreatedAt());
    }
}