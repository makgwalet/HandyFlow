package za.co.handyflow.platform.compliancetender.application.internal.submission;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.compliancetender.domain.model.ComplianceRegistration;
import za.co.handyflow.platform.compliancetender.domain.repository.ComplianceRegistrationRepository;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class TenantDetailsCompanyProfileProvider implements CompanyProfileProvider {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final TenantFacade tenantFacade;
    private final ComplianceRegistrationRepository registrations;

    @Override
    public String currentProfileText(TenantId tenantId) {
        TenantDetails d = tenantFacade.findTenantDetails(tenantId).orElse(null);
        if (d == null) return "";
        List<String> lines = new ArrayList<>();
        add(lines, "Company", d.companyName());
        add(lines, "VAT number", d.vatNumber());
        add(lines, "Telephone", d.phone());
        add(lines, "Email", d.email());
        if (d.address() != null && !d.address().isEmpty()) {
            add(lines, "Address", String.join(", ", d.address().values().stream().filter(v -> v != null && !v.isBlank()).toList()));
        }
        List<String> regs = new ArrayList<>();
        for (ComplianceRegistration r : registrations.findAllForTenant(tenantId)) {
            if (!"ACTIVE".equals(r.getStatus())) continue; // only what is in force goes in front of an evaluator
            String number = r.getRegistrationNumber() == null || r.getRegistrationNumber().isBlank() ? "Registered" : r.getRegistrationNumber().trim();
            String valid = r.getExpiryDate() == null ? "" : "  (valid until " + r.getExpiryDate().format(DAY) + ")";
            regs.add(r.getAuthority() + " " + r.getRegistrationType() + ": " + number + valid);
        }
        if (!regs.isEmpty()) {
            lines.add("");
            lines.add("## Registrations and standing");
            lines.addAll(regs);
        }
        return String.join("\n", lines);
    }

    private static void add(List<String> lines, String label, String value) {
        if (value != null && !value.isBlank()) lines.add(label + ": " + value.trim());
    }
}
