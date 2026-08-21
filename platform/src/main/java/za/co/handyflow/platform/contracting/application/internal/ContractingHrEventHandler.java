package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.contracting.dto.CreateContractRequest;
import za.co.handyflow.platform.contracting.dto.TemplateResponse;
import za.co.handyflow.platform.hr.EmployeeCreatedEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * FIX: backlog 3.3 — "contracting ↔ HR wiring unconfirmed." Confirmed
 * genuinely disconnected (see hr.EmployeeCreatedEvent's own Javadoc for
 * the investigation). This listener is the actual fix: reacts to a new
 * employee by auto-creating a DRAFT contract from the system's
 * "Employment Contract" (BCEA-aligned) template.
 * <p>
 * DELIBERATELY A DRAFT WITH SEVERAL VARIABLES LEFT UNRESOLVED, NOT A
 * COMPLETE, SEND-READY CONTRACT. The EMPLOYMENT template
 * (ContractTemplateSeeder) declares 16 variables. HrEmployee, at the
 * point this event fires, only actually captures 5 of them:
 * employee_name, employee_id_number, start_date, job_title, basic_salary.
 * The rest — employer_name/reg/address, employee_address, job_duties,
 * probation_months, working_hours, pay_day, annual_leave_days,
 * notice_period_weeks, city_name — have no corresponding captured data
 * anywhere in HR (or, for the employer fields, anywhere this module can
 * currently reach — contracting has no dependency on identity/tenant
 * profile data). Guessing plausible-sounding defaults for a legal
 * document's own terms (how many leave days, what the notice period is)
 * would be actively wrong, not just incomplete, so those tokens are left
 * as literal {{unresolved}} placeholders in the body instead — exactly
 * the same "leave blank to fill in later" state ContractsTab.tsx already
 * supports and ContractingService.updateContract() already exists to
 * finish. HR opens the auto-created draft, fills in the rest through the
 * existing edit flow, and sends it for signing when ready — nothing new
 * to build for that half of the workflow, it already exists.
 * <p>
 * createdByUserId is passed as null (system-triggered, no human actor) —
 * same "null = system" convention already used elsewhere in this
 * codebase for system-generated records (e.g.
 * crm.CustomerActivity.systemEvent()). Not independently verified against
 * Contract.create()'s own null-handling for this specific field; flagging
 * that rather than asserting it's certainly safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ContractingHrEventHandler {

    private static final String EMPLOYMENT_CONTRACT_TYPE = "EMPLOYMENT";

    private final ContractingService contractingService;

    @ApplicationModuleListener
    void onEmployeeCreated(EmployeeCreatedEvent event) {
        try {
            TemplateResponse employmentTemplate = contractingService
                    .getTemplates(event.tenantId())
                    .stream()
                    .filter(t -> EMPLOYMENT_CONTRACT_TYPE.equals(t.contractType()) && t.isSystem())
                    .findFirst()
                    .orElse(null);

            if (employmentTemplate == null) {
                log.warn("[Contracting] No system EMPLOYMENT template found for tenant={} " +
                        "— skipping auto-draft for new employee={}", event.tenantId(), event.employeeId());
                return;
            }

            Map<String, String> variables = new HashMap<>();
            String fullName = ((event.firstName() != null ? event.firstName() : "") + " "
                    + (event.lastName() != null ? event.lastName() : "")).strip();
            if (!fullName.isBlank()) variables.put("employee_name", fullName);
            if (event.idNumber() != null) variables.put("employee_id_number", event.idNumber());
            if (event.jobTitle() != null) variables.put("job_title", event.jobTitle());
            if (event.startDate() != null) variables.put("start_date", event.startDate().toString());
            if (event.grossSalary() != null) variables.put("basic_salary", event.grossSalary().toPlainString());

            var req = new CreateContractRequest(
                    "Employment Contract — " + (fullName.isBlank() ? "New Employee" : fullName),
                    EMPLOYMENT_CONTRACT_TYPE,
                    employmentTemplate.id(),
                    null,           // body — resolved from the template, not supplied raw
                    variables,
                    null,           // valueAmount — not applicable to an employment contract
                    event.startDate(),
                    null,           // endDate — permanent by default; HR sets this if fixed-term
                    false,          // autoRenew
                    null,           // renewalNoticeDays
                    "Auto-generated on employee creation — complete remaining fields before sending for signing."
            );

            contractingService.createContract(event.tenantId(), req, null);

            log.info("[Contracting] Auto-created DRAFT employment contract for new employee={} tenant={}",
                    event.employeeId(), event.tenantId());
        } catch (Exception e) {
            // Same principle as every other cross-module side-effect hookup
            // in this codebase: the employee is already saved and committed
            // by the time this listener runs — a contract-generation
            // failure must never be allowed to look like it affected that.
            log.error("[Contracting] Failed to auto-create employment contract for employee={} tenant={}: {}",
                    event.employeeId(), event.tenantId(), e.getMessage(), e);
        }
    }
}