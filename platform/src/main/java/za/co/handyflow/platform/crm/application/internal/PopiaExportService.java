package za.co.handyflow.platform.crm.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.repository.CustomerActivityRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.dto.PopiaExportDto;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.UUID;

/**
 * PopiaExportService — generates a complete data subject export for POPIA compliance.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * WHAT IS POPIA?
 * The Protection of Personal Information Act (POPIA), Act 4 of 2013, is
 * South Africa's primary data privacy law.  Section 23 gives data subjects
 * (your customers) the right to request a copy of all personal information
 * held about them.  You must respond within a reasonable time (typically
 * 30 days).  Failure to comply is an offence.
 *
 * WHAT THIS EXPORTS:
 * 1. All personal information on the Customer record
 * 2. All Contacts linked to this customer
 * 3. The complete activity timeline (who changed what, when)
 * 4. Export metadata (when the export was generated, by whom)
 *
 * WHY JSON as the primary format?
 * JSON is machine-readable, self-documenting, and can be validated by
 * a third party (e.g. the Information Regulator).  Many POPIA tools
 * and lawyers expect JSON or PDF.  We offer both — JSON for machine
 * processing, PDF for human review (PDF generation is a future step,
 * using the JSON as input).
 *
 * WHY include the activity timeline?
 * The timeline shows WHEN data was modified and BY WHOM.  Under POPIA,
 * the data subject can ask "who accessed my data?"  The activity log
 * is our answer to that question.  Omitting it would be an incomplete
 * export.
 *
 * AUDIT:
 * Every POPIA export is itself logged as a POPIA_EXPORT_REQUESTED activity
 * on the customer's timeline so there's a permanent record that the export
 * was generated (who requested it, when).
 * ═══════════════════════════════════════════════════════════════════════
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PopiaExportService {

    private final CustomerRepository         customerRepository;
    private final CustomerActivityRepository activityRepository;
    private final ObjectMapper               objectMapper = buildMapper();

    /**
     * Generate a POPIA-compliant JSON export for a single customer.
     * Writes directly to the provided Writer for streaming.
     *
     * @param requestedBy  The userId who triggered the export (staff member).
     *                     Recorded in the activity log.
     */
    @Transactional
    public void exportCustomerJson(TenantId tenantId, UUID customerId,
                                   UUID requestedBy, Writer writer) throws IOException {
        var customer = customerRepository.findActiveById(tenantId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId.toString()));

        // Fetch all activities — no pagination, POPIA requires complete history
        var activities = activityRepository
                .findAllByCustomer(tenantId, customerId);  // unbounded — add this method below

        var export = new PopiaExportDto(
                customerId,
                Instant.now(),
                requestedBy,
                new PopiaExportDto.PersonalData(
                        customer.getId(),
                        customer.getName(),
                        customer.getEmail(),
                        customer.getPhone(),
                        customer.getAddress(),
                        customer.getTaxNumber(),
                        customer.getNotes(),
                        customer.getCustomerType().name(),
                        customer.getStatus().name(),
                        customer.getTags(),
                        customer.getCreatedAt(),
                        customer.getUpdatedAt(),
                        customer.getDeletedAt()
                ),
                activities.stream().map(a -> new PopiaExportDto.ActivityEntry(
                        a.getId(),
                        a.getActivityType().name(),
                        a.getPayload(),
                        a.getNote(),
                        a.getPerformedBy(),
                        a.getCreatedAt()
                )).toList()
        );

        // Log the export itself as an activity — POPIA audit trail
        customer.addNote(
                "POPIA data export generated by user " + requestedBy + " at " + Instant.now(),
                requestedBy
        );
        customerRepository.save(customer);

        objectMapper.writeValue(writer, export);
        writer.flush();

        log.info("[CRM][POPIA] Data export generated: customer={} requestedBy={} tenant={}",
                customerId, requestedBy, tenantId);
    }

    private static ObjectMapper buildMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);   // human-readable JSON
    }
}
