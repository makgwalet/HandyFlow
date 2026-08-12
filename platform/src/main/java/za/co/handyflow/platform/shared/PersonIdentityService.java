package za.co.handyflow.platform.shared;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves or creates a canonical GlobalPersonId for a human, given
 * whatever contact details a module currently has on hand. This is the
 * generalized version of the "shared identifier, not shared entity"
 * principle Section 22.3 established and HrFacade (Section 28) first
 * implemented — PersonIdentityService is the reusable capability behind
 * it, not tied to HR specifically.
 * <p>
 * WHAT THIS DOES NOT DO, DELIBERATELY: it does not merge, own, or expose
 * any business data about the person. HR's HrEmployee keeps its own
 * payroll fields; Security's SecurityGuard keeps its own PSiRA number and
 * shift patterns; Recruiter's RecApplicant keeps its own application
 * history. This service only answers "have we seen this human before,
 * and if so, what's their reference id" — each module still owns its own
 * tailored view, linked by that id, exactly the anti-pattern-avoidance
 * reasoning in Section 22.3 that rejected a single shared Person entity.
 * <p>
 * MATCH STRATEGY: id number first (SA ID number is the strongest natural
 * key available for a person in this domain — near-unique, rarely
 * mistyped consistently), falling back to email if no id number is
 * available or matches (weaker signal — logged when used, so a false
 * match is traceable). If neither matches an existing record, a new
 * PersonIdentity is created. Whichever fields the caller supplied that
 * were previously missing get filled in (see
 * PersonIdentity.fillMissingFields()) — so a person resolved first via
 * email-only (e.g. a recruitment applicant) gets their id number added
 * automatically once a module that captures it (e.g. HR, at hiring)
 * resolves the same person.
 * <p>
 * WORKED EXAMPLE — closing the Security->HR gap (Section 15.4):
 * when Security's onboarding flow eventually gets built, it would call
 * personIdentityService.resolveOrCreate(tenantId, guardIdNumber,
 * guardEmail, guardPhone, guardFullName) to get a GlobalPersonId, then
 * pass that id (as an external reference, e.g. a future
 * CreateEmployeeRequest.globalPersonId field) into HrFacade.createEmployee().
 * HR would store that id on its own HrEmployee record. If the same
 * person is later looked up from Security's side again (e.g. a
 * returning guard), resolveOrCreate() with their id number returns the
 * SAME GlobalPersonId, and Security can ask "does this person already
 * have an HR record" via that shared reference — without either module
 * reading the other's entity or database table directly.
 * <p>
 * NOT YET DONE — SCOPE OF WHAT THIS DELIVERS: this ships the resolution
 * capability itself. It does NOT yet add a globalPersonId column to
 * HrEmployee, SecurityGuard, or RecApplicant, and does NOT yet wire the
 * Security->HR onboarding flow described above — those are real,
 * separate follow-up changes (schema migrations per module, plus the
 * actual onboarding flow, which doesn't exist yet per Section 15.4).
 * This is the reusable foundation those changes would build on, not a
 * claim that they're done.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonIdentityService {

    private final PersonIdentityRepository repo;

    @Transactional
    public GlobalPersonId resolveOrCreate(TenantId tenantId, String idNumber,
                                          String email, String phone, String fullName) {

        if (idNumber != null && !idNumber.isBlank()) {
            var existing = repo.findByTenantAndIdNumber(tenantId.getValue(), idNumber);
            if (existing.isPresent()) {
                PersonIdentity p = existing.get();
                p.fillMissingFields(idNumber, email, phone);
                return GlobalPersonId.of(p.getId());
            }
        }

        if (email != null && !email.isBlank()) {
            var existing = repo.findByTenantAndEmail(tenantId.getValue(), email);
            if (existing.isPresent()) {
                log.info("PersonIdentityService: matched by email (weaker signal than id number) " +
                        "for tenant={} email={}", tenantId.getValue(), email);
                PersonIdentity p = existing.get();
                p.fillMissingFields(idNumber, email, phone);
                return GlobalPersonId.of(p.getId());
            }
        }

        PersonIdentity created = PersonIdentity.create(tenantId, idNumber, email, phone, fullName);
        repo.save(created);
        log.info("PersonIdentityService: created new identity={} for tenant={}",
                created.getId(), tenantId.getValue());
        return GlobalPersonId.of(created.getId());
    }
}