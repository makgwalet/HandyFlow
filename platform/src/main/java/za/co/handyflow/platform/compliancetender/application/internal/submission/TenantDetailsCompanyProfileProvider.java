package za.co.handyflow.platform.compliancetender.application.internal.submission;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TenantDetailsCompanyProfileProvider implements CompanyProfileProvider {

    private final TenantFacade tenantFacade;

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
        return String.join("\n", lines);
    }

    private static void add(List<String> lines, String label, String value) {
        if (value != null && !value.isBlank()) lines.add(label + ": " + value.trim());
    }
}
