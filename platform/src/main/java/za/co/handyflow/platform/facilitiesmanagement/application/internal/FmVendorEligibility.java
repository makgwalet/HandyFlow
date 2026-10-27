package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmVendor;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmVendorDocumentRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.List;

/** Whether a vendor may be given work right now: approved, and every required document in force. */
@Component
@RequiredArgsConstructor
public class FmVendorEligibility {

    private final FmVendorDocumentRepository documentRepository;

    /** Reasons the vendor cannot receive work; empty when it can. */
    public List<String> problems(TenantId tenantId, FmVendor vendor) {
        List<String> out = new ArrayList<>();
        if (!vendor.isApproved()) {
            out.add("vendor is " + vendor.getOnboardingStatus() + (vendor.getSuspendedReason() != null ? " (" + vendor.getSuspendedReason() + ")" : ""));
        }
        out.addAll(FmVendorCompliance.evaluate(documentRepository.findForVendor(tenantId, vendor.getId()), FmTime.today()).problems());
        return out;
    }
}
