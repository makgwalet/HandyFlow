package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.model.Contact;
import za.co.handyflow.platform.crm.domain.repository.ContactRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.dto.ContactRequest;
import za.co.handyflow.platform.crm.dto.ContactResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The people at a customer. Exactly one live contact is the main contact: the first one added, or whichever was last made main.
 * Removing the main contact promotes the one who was added first. Deleting is soft.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;
    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public List<ContactResponse> list(TenantId tenantId, UUID customerId) {
        requireCustomer(tenantId, customerId);
        return sorted(contactRepository.findActiveByCustomer(tenantId, customerId)).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ContactResponse create(TenantId tenantId, UUID customerId, ContactRequest r) {
        var customer = customerRepository.findActiveById(tenantId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId.toString()));
        List<Contact> existing = contactRepository.findActiveByCustomer(tenantId, customerId);
        String email = normaliseEmail(r.email());
        rejectDuplicateEmail(existing, email, null);
        boolean main = r.primary() || existing.isEmpty();
        if (main) existing.forEach(c -> c.markPrimary(false));
        Contact contact = Contact.create(tenantId, customer, r.firstName(), r.lastName(), email, r.phone(), r.jobTitle(), main);
        contact.update(r.firstName(), r.lastName(), email, r.phone(), r.jobTitle(), r.notes());
        contactRepository.save(contact);
        log.info("[CRM] Contact added customer={} tenant={}", customerId, tenantId);
        return toResponse(contact);
    }

    @Transactional
    public ContactResponse update(TenantId tenantId, UUID customerId, UUID id, ContactRequest r) {
        Contact contact = find(tenantId, customerId, id);
        List<Contact> all = contactRepository.findActiveByCustomer(tenantId, customerId);
        String email = normaliseEmail(r.email());
        rejectDuplicateEmail(all, email, id);
        if (contact.isPrimary() && !r.primary()) {
            throw new IllegalStateException("Make another contact the main contact first");
        }
        if (r.primary() && !contact.isPrimary()) {
            all.stream().filter(c -> !c.getId().equals(id)).forEach(c -> c.markPrimary(false));
            contact.markPrimary(true);
        }
        contact.update(r.firstName(), r.lastName(), email, r.phone(), r.jobTitle(), r.notes());
        return toResponse(contact);
    }

    @Transactional
    public ContactResponse makePrimary(TenantId tenantId, UUID customerId, UUID id) {
        Contact contact = find(tenantId, customerId, id);
        contactRepository.findActiveByCustomer(tenantId, customerId).stream()
                .filter(c -> !c.getId().equals(id)).forEach(c -> c.markPrimary(false));
        contact.markPrimary(true);
        return toResponse(contact);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID customerId, UUID id) {
        Contact contact = find(tenantId, customerId, id);
        boolean wasMain = contact.isPrimary();
        contact.markPrimary(false);
        contact.softDelete(currentUserId());
        if (wasMain) {
            contactRepository.findActiveByCustomer(tenantId, customerId).stream()
                    .filter(c -> !c.getId().equals(id))
                    .min(Comparator.comparing(Contact::getCreatedAt))
                    .ifPresent(c -> c.markPrimary(true));
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private void requireCustomer(TenantId tenantId, UUID customerId) {
        if (!customerRepository.existsActiveById(tenantId, customerId)) {
            throw new ResourceNotFoundException("Customer", customerId.toString());
        }
    }

    private Contact find(TenantId tenantId, UUID customerId, UUID id) {
        return contactRepository.findActiveById(tenantId, customerId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", id.toString()));
    }

    private static String normaliseEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase();
    }

    private static void rejectDuplicateEmail(List<Contact> existing, String email, UUID ignoreId) {
        if (email == null) return;
        boolean taken = existing.stream().anyMatch(c -> !c.getId().equals(ignoreId) && email.equalsIgnoreCase(c.getEmail()));
        if (taken) throw new IllegalStateException("This customer already has a contact with that email");
    }

    private static List<Contact> sorted(List<Contact> contacts) {
        return contacts.stream()
                .sorted(Comparator.comparing(Contact::isPrimary).reversed()
                        .thenComparing(Contact::getLastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Contact::getFirstName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) return null;
        try {
            return UUID.fromString(auth.getName());
        } catch (Exception e) {
            return null;
        }
    }

    private ContactResponse toResponse(Contact c) {
        return new ContactResponse(c.getId(), c.getCustomer().getId(), c.getFirstName(), c.getLastName(), c.getFullName(),
                c.getEmail(), c.getPhone(), c.getJobTitle(), c.isPrimary(), c.getNotes());
    }
}
