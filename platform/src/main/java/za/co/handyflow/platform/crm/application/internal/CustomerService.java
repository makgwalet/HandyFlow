package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.model.*;
import za.co.handyflow.platform.crm.domain.repository.CustomerActivityRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.dto.*;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CustomerService — application-layer orchestrator for the CRM module.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * ARCHITECTURE NOTES (for junior devs):
 *
 * WHERE does business logic live?
 * ┌──────────────────┬────────────────────────────────────────────────┐
 * │ CustomerService  │ Orchestration: fetch, validate, save, map      │
 * │ Customer (entity)│ Domain rules: what is valid state, how state   │
 * │                  │ transitions happen, what gets recorded         │
 * └──────────────────┴────────────────────────────────────────────────┘
 *
 * The service should NOT contain business rules like:
 *   "a deleted customer can't be deleted again"
 *   "email must be normalised to lowercase"
 * Those live in the Customer entity (domain model).
 *
 * The service SHOULD contain:
 *   "does another customer with this email already exist?"
 *   "which page of results did we ask for?"
 *   "map domain object to DTO for the response"
 *
 * SECURITY CONTEXT:
 * Every mutation now resolves the acting user from Spring Security.
 * The original code passed `null` to softDelete() — that loses the
 * audit trail.  `currentUserId()` is a helper that safely extracts
 * the UUID from whatever authentication principal your app uses.
 * ═══════════════════════════════════════════════════════════════════════
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository            customerRepository;
    private final CustomerActivityRepository    activityRepository;
    private final CustomerNameSimilarityChecker similarityChecker;
    private final EmailService                  emailService;
    private final TenantAdminRecipients         tenantAdminRecipients;
    private final NotificationService           notificationService;
    private final UserRecipientResolver         userRecipientResolver;

    // ══════════════════════════════════════════════════════════════════════
    // READ operations
    // ══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Page<CustomerResponse> getCustomers(TenantId tenantId, String search,
                                               Boolean mine, Pageable pageable) {
        // FIX: backlog 4.1 — "my leads" filter. Deliberately takes priority
        // over search when both are somehow set (the controller only ever
        // sends one or the other in practice, but this keeps the method's
        // own behaviour unambiguous rather than silently picking one).
        if (Boolean.TRUE.equals(mine)) {
            return customerRepository
                    .findAllActiveForOwnerOrUnassigned(tenantId, currentUserId(), pageable)
                    .map(this::toResponse);
        }
        var page = (search == null || search.isBlank())
                ? customerRepository.findAllActive(tenantId, pageable)
                : customerRepository.searchActive(tenantId, search.strip(), pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(TenantId tenantId, UUID id) {
        return customerRepository.findActiveById(tenantId, id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id.toString()));
    }

    /**
     * Returns soft-deleted customers for the "Deleted Customers" view.
     * WHY expose this? Accidental deletes happen.  Staff need a way to
     * see and restore them without opening a support ticket.
     */
    @Transactional(readOnly = true)
    public Page<CustomerResponse> getDeletedCustomers(TenantId tenantId, Pageable pageable) {
        return customerRepository.findAllDeleted(tenantId, pageable)
                .map(this::toResponse);
    }

    /**
     * Activity timeline — paginated, newest first.
     * WHY paginated?  Active customers accumulate hundreds of events.
     * Returning all of them in one shot would be slow and expensive.
     */
    @Transactional(readOnly = true)
    public Page<CustomerActivityResponse> getActivities(TenantId tenantId,
                                                        UUID customerId,
                                                        Pageable pageable) {
        // Verify the customer belongs to this tenant first
        if (!customerRepository.existsActiveById(tenantId, customerId)) {
            throw new ResourceNotFoundException("Customer", customerId.toString());
        }
        return activityRepository.findByCustomer(tenantId, customerId, pageable)
                .map(this::toActivityResponse);
    }

    /**
     * FIX: "no lead/pipeline stage tracking" gap. Separate small endpoint/DTO
     * rather than folding into CustomerResponse.
     */
    @Transactional(readOnly = true)
    public StageResponse getStage(TenantId tenantId, UUID id) {
        var customer = requireActive(tenantId, id);
        return toStageResponse(customer);
    }

    // ══════════════════════════════════════════════════════════════════════
    // WRITE operations
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public CustomerResponse createCustomer(TenantId tenantId, CreateCustomerRequest request) {
        // Guard 1: exact email uniqueness (hard constraint, 409 on match).
        // The DB partial unique index (V8) is the hard backstop against race conditions.
        if (request.email() != null && !request.email().isBlank()) {
            String normalised = request.email().strip().toLowerCase();
            if (customerRepository.existsActiveByEmail(tenantId, normalised)) {
                throw new ConflictException(
                        "A customer with email '" + request.email() + "' already exists"
                );
            }
        }

        // Guard 2: fuzzy name similarity warning (soft check, 409 if match found).
        //
        // WHY warn on create but not hard-block?
        // Two branches of the same corporate group are legitimately different
        // customers — "Tau Mining Johannesburg" and "Tau Mining Pretoria" are
        // intentional entries.  The staff member sees the warning and can
        // either use a more distinct name or confirm the duplicate is intentional
        // by calling createCustomerConfirmed() with skipDuplicateCheck=true.
        //
        // For now we return a 409 with a clear message so the frontend can
        // display it in the error banner.  A future enhancement could return
        // HTTP 200 with a "possibleDuplicates" list so the user can confirm
        // inline without retyping.
        if (request.name() != null && !request.name().isBlank()) {
            var existingNames = customerRepository.findAllActiveNames(tenantId);
            similarityChecker.findProbableDuplicate(request.name(), existingNames)
                    .ifPresent(match -> {
                        throw new ConflictException(
                                "A customer named '" + match + "' already exists and looks " +
                                        "very similar to '" + request.name() + "'. " +
                                        "If this is a different company, make the name more distinct " +
                                        "(e.g. add branch or city). " +
                                        "If it is the same company, find and edit the existing record."
                        );
                    });
        }

        var customer = Customer.create(
                tenantId,
                request.name(),
                request.email(),
                request.phone(),
                addressToMap(request.address()),
                request.taxNumber(),
                request.notes(),
                request.customerType(),
                currentUserId()
        );

        // Apply initial tags if provided
        if (request.tags() != null) {
            request.tags().forEach(tag -> customer.addTag(tag, currentUserId()));
        }

        customerRepository.save(customer);
        log.info("[CRM] Customer created id={} name='{}' tenant={} by={}",
                customer.getId(), customer.getName(), tenantId, currentUserId());

        // FIX: backlog 4.1 — this used to say "Customer has no
        // assignment/ownership field at all... inventing one would be
        // scope creep" — that's no longer true; ownership shipped in 4.1.
        // notifyNewLead() now routes to the owner first (see its own
        // Javadoc), tenant admins only as a fallback.
        if (customer.getCustomerType() == CustomerType.LEAD) {
            notifyNewLead(tenantId, customer);
        }

        return toResponse(customer);
    }

    @Transactional
    public CustomerResponse updateCustomer(TenantId tenantId, UUID id,
                                           UpdateCustomerRequest request) {
        var customer = requireActive(tenantId, id);
        var actingUser = currentUserId();

        // Email uniqueness: only check if email is actually changing
        if (request.email() != null && !request.email().isBlank()) {
            var normalised = request.email().strip().toLowerCase();
            if (!normalised.equals(customer.getEmail())) {
                if (customerRepository.existsActiveByEmailExcluding(tenantId, normalised, id)) {
                    throw new ConflictException(
                            "A customer with email '" + request.email() + "' already exists"
                    );
                }
            }
        }

        customer.update(
                request.name(),
                request.email(),
                request.phone(),
                addressToMap(request.address()),
                request.taxNumber(),
                request.notes(),
                actingUser
        );

        // Status change via update (staff can set ACTIVE/INACTIVE/BLOCKED)
        if (request.status() != null && request.status() != customer.getStatus()) {
            customer.changeStatus(request.status(), actingUser);
        }

        customerRepository.save(customer);
        log.info("[CRM] Customer updated id={} tenant={} by={}", id, tenantId, actingUser);
        return toResponse(customer);
    }

    /**
     * Soft-delete a customer.
     *
     * WHY check for active bookings and invoices before deleting?
     * The original code deleted silently even if the customer had upcoming
     * bookings or unpaid invoices.  Staff would then find orphaned bookings
     * and invoices with no customer record attached, causing support tickets.
     *
     * We now refuse the delete with a clear, actionable error message.
     * Staff must cancel/complete bookings and settle invoices first.
     *
     * WHY ConflictException (409)?
     * The request is well-formed — valid customer ID, correct permissions.
     * The problem is a BUSINESS CONFLICT: deleting would orphan related
     * records.  409 is the correct HTTP status for this.
     *
     * WHY two separate checks instead of one combined query?
     * Separate checks give separate, actionable messages:
     *   "3 active bookings exist. Cancel them first."
     *   "2 unpaid invoices exist. Settle them first."
     * A combined "has related records" message forces staff to go hunting.
     */
    @Transactional
    public void softDeleteCustomer(TenantId tenantId, UUID id) {
        var customer   = requireActive(tenantId, id);
        var actingUser = currentUserId();

        // Guard 1: active bookings
        long activeBookings = customerRepository.countActiveBookings(tenantId, id);
        if (activeBookings > 0) {
            throw new ConflictException(
                    "Cannot delete '" + customer.getName() + "' — " + activeBookings +
                            " active booking" + (activeBookings == 1 ? "" : "s") +
                            " exist. Cancel or complete them first."
            );
        }

        // Guard 2: unpaid invoices
        long unpaidInvoices = customerRepository.countUnpaidInvoices(tenantId, id);
        if (unpaidInvoices > 0) {
            throw new ConflictException(
                    "Cannot delete '" + customer.getName() + "' — " + unpaidInvoices +
                            " unpaid invoice" + (unpaidInvoices == 1 ? "" : "s") +
                            " exist. Settle or void them first."
            );
        }

        customer.softDelete(actingUser);
        customerRepository.save(customer);
        log.info("[CRM] Customer soft-deleted id={} tenant={} by={}", id, tenantId, actingUser);
    }

    /**
     * Restore a previously soft-deleted customer.
     *
     * WHY check email uniqueness on restore?
     * While the customer was deleted, another customer might have been
     * created with the same email.  Restoring would then violate the
     * partial unique index and cause a cryptic DB error.
     * We check first and throw a friendly ConflictException instead.
     */
    @Transactional
    public CustomerResponse restoreCustomer(TenantId tenantId, UUID id) {
        var customer = customerRepository.findDeletedById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Deleted customer", id.toString()));

        // Check email won't conflict with an active customer
        if (customer.getEmail() != null) {
            if (customerRepository.existsActiveByEmail(tenantId, customer.getEmail())) {
                throw new ConflictException(
                        "Cannot restore: another active customer already uses email '"
                                + customer.getEmail() + "'. Update the email before restoring."
                );
            }
        }

        var actingUser = currentUserId();
        customer.restore(actingUser);
        customerRepository.save(customer);
        log.info("[CRM] Customer restored id={} tenant={} by={}", id, tenantId, actingUser);
        return toResponse(customer);
    }

    /**
     * Add a timestamped, attributed note to a customer's timeline.
     * WHY not just update the notes text field?
     * The timeline note records WHO added it and WHEN.
     * The notes field is an unattributed scratch pad.
     * Both have their place.
     */
    @Transactional
    public CustomerActivityResponse addNote(TenantId tenantId, UUID id, AddNoteRequest request) {
        var customer = requireActive(tenantId, id);
        customer.addNote(request.note(), currentUserId());
        customerRepository.save(customer);
        // The most recent activity is the one we just added
        var latest = customer.getActivities().get(0);
        return toActivityResponse(latest);
    }

    @Transactional
    public CustomerResponse addTag(TenantId tenantId, UUID id, String tag) {
        var customer = requireActive(tenantId, id);
        customer.addTag(tag, currentUserId());
        customerRepository.save(customer);
        return toResponse(customer);
    }

    @Transactional
    public CustomerResponse removeTag(TenantId tenantId, UUID id, String tag) {
        var customer = requireActive(tenantId, id);
        customer.removeTag(tag, currentUserId());
        customerRepository.save(customer);
        return toResponse(customer);
    }

    /**
     * FIX: backlog 4.1 — "no lead ownership/assignment" gap. Unlike
     * changeStage(), this is valid for any customer type, not just LEAD —
     * see Customer.assignOwner()'s own Javadoc for why.
     */
    @Transactional
    public CustomerResponse assignOwner(TenantId tenantId, UUID id, UUID newOwnerId) {
        var customer = requireActive(tenantId, id);
        customer.assignOwner(newOwnerId, currentUserId());
        customerRepository.save(customer);
        log.info("[CRM] Customer owner changed id={} tenant={} newOwner={} by={}",
                id, tenantId, newOwnerId, currentUserId());
        return toResponse(customer);
    }

    /**
     * FIX: backlog 4.2 — "no deal value / expected close date" gap. This
     * service method was missing even though Customer.updateDeal() (the
     * domain method) and the CustomerResponse fields both existed —
     * confirmed on inspection and added now. Mirrors assignOwner()'s shape
     * exactly.
     */
    @Transactional
    public CustomerResponse updateDeal(TenantId tenantId, UUID id,
                                       BigDecimal dealValue, LocalDate expectedCloseDate) {
        var customer = requireActive(tenantId, id);
        customer.updateDeal(dealValue, expectedCloseDate, currentUserId());
        customerRepository.save(customer);
        log.info("[CRM] Customer deal updated id={} tenant={} by={}", id, tenantId, currentUserId());
        return toResponse(customer);
    }

    @Transactional
    public StageResponse changeStage(TenantId tenantId, UUID id, LeadStage newStage) {
        var customer = requireActive(tenantId, id);
        customer.changeStage(newStage, currentUserId());
        return toStageResponse(customer);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Private helpers
    // ══════════════════════════════════════════════════════════════════════

    private Customer requireActive(TenantId tenantId, UUID id) {
        return customerRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id.toString()));
    }

    /**
     * Resolve the currently authenticated user's ID from Spring Security.
     *
     * WHY not accept userId as a parameter?
     * If we accept it as a parameter, every controller caller must pass it.
     * With a security context helper, it's resolved once here.
     *
     * WHY return null if no auth?
     * Some operations (e.g. scheduled jobs, system migrations) run without
     * a user principal.  Returning null is correct: the activity log will
     * show performedBy=null meaning "system".  We never silently lose the
     * user when one is present — the cast to UUID will catch that.
     */
    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return null;
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (Exception e) {
            log.warn("[CRM] Could not resolve userId from SecurityContext: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Convert typed AddressRequest to Map<String,String> for JSONB storage.
     * WHY not store the record directly?
     * JPA/Hibernate maps Map<String,String> to JSONB natively.
     * We can't use a Java record directly as a JSONB value without a custom
     * AttributeConverter (unnecessary complexity for a simple address).
     */
    private Map<String, String> addressToMap(AddressRequest addr) {
        if (addr == null) return null;
        var map = new java.util.LinkedHashMap<String, String>();
        if (addr.street()     != null) map.put("street",     addr.street());
        if (addr.suburb()     != null) map.put("suburb",     addr.suburb());
        if (addr.city()       != null) map.put("city",       addr.city());
        if (addr.province()   != null) map.put("province",   addr.province());
        if (addr.postalCode() != null) map.put("postalCode", addr.postalCode());
        return map.isEmpty() ? null : map;
    }

    /**
     * FIX: backlog 4.2 — dealValue/expectedCloseDate were missing from
     * this mapper even though both the entity field and the DTO field
     * existed — confirmed on inspection and added now.
     */
    private CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(
                c.getId(),
                c.getName(),
                c.getEmail(),
                c.getPhone(),
                c.getAddress(),
                c.getTaxNumber(),
                c.getNotes(),
                c.getOwnerId(),
                c.getDealValue(),
                c.getExpectedCloseDate(),
                c.getCustomerType(),
                c.getStatus(),
                Set.copyOf(c.getTags()),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    private CustomerActivityResponse toActivityResponse(CustomerActivity a) {
        return new CustomerActivityResponse(
                a.getId(),
                a.getActivityType(),
                a.getPayload(),
                a.getNote(),
                a.getPerformedBy(),
                a.getCreatedAt()
        );
    }

    private StageResponse toStageResponse(Customer customer) {
        return new StageResponse(
                customer.getId(),
                customer.getCustomerType().name(),
                customer.getPipelineStage() != null ? customer.getPipelineStage().name() : null
        );
    }

    // FIX: backlog 4.1 — was tenant-admins-only because Customer had no
    // ownership field at all when this was originally written. Now routes
    // to the owner first — same "specific person first, tenant admins as
    // fallback" shape hr.HrService.notifyApprover() already established
    // for leave-approval routing (manager if resolvable, else tenant
    // admins) — reused here for consistency rather than inventing a
    // second fallback pattern for the same kind of decision.
    private void notifyNewLead(TenantId tenantId, Customer customer) {
        try {
            List<Recipient> recipients;
            if (customer.getOwnerId() != null) {
                recipients = userRecipientResolver.resolveUser(tenantId, customer.getOwnerId())
                        .map(List::of)
                        .orElse(null);
            } else {
                recipients = null;
            }
            if (recipients == null || recipients.isEmpty()) {
                recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
            }
            if (recipients.isEmpty()) {
                log.info("[CRM] New lead={} created but no owner or admin recipients could be resolved for tenant={} — not notified",
                        customer.getId(), tenantId);
                return;
            }

            String message = "A new lead has been added to the CRM: " + customer.getName();
            if (customer.getEmail() != null && !customer.getEmail().isBlank()) {
                message += " (" + customer.getEmail() + ")";
            }
            if (customer.getPhone() != null && !customer.getPhone().isBlank()) {
                message += " — " + customer.getPhone();
            }
            message += ". Open the CRM to follow up.";

            notificationService.send(NotificationRequest.builder()
                    .tenantId(tenantId)
                    .type(NotificationType.NEW_LEAD_ASSIGNED)
                    .title("New lead: " + customer.getName())
                    .message(message)
                    .actionUrl("/crm/customers/" + customer.getId())
                    .sourceModule("crm")
                    .sourceEntityId(customer.getId().toString())
                    .recipients(recipients)
                    .build());
        } catch (Exception e) {
            // Same principle as every other notification hookup in this
            // codebase: the customer is already saved above and must not
            // be undone by a notification failure.
            log.warn("[CRM] New-lead notification failed for customer={} tenant={}: {}",
                    customer.getId(), tenantId, e.getMessage());
        }
    }

    private String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}