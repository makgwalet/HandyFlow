package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.facilities.domain.model.FacilityVendor;
import za.co.handyflow.platform.facilities.domain.repository.FacilityVendorDocumentRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.List;

/** Whether a vendor may be given work right now: approved, and every required document in force. */
@Component
@RequiredArgsConstructor
public class FacilityVendorEligibility {

    private final FacilityVendorDocumentRepository documentRepository;

    /** Reasons the vendor cannot receive work; empty when it can. */
    public List<String> problems(TenantId tenantId, FacilityVendor vendor) {
        List<String> out = new ArrayList<>();
        if (!vendor.isApproved()) {
            out.add("vendor is " + vendor.getOnboardingStatus() + (vendor.getSuspendedReason() != null ? " (" + vendor.getSuspendedReason() + ")" : ""));
        }
        out.addAll(FacilityVendorCompliance.evaluate(documentRepository.findForVendor(tenantId, vendor.getId()), FacilityTime.today()).problems());
        return out;
    }
}
