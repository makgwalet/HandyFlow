package za.co.handyflow.platform.compliancetender.application.internal.submission;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.compliancetender.domain.model.Tender;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderPersonnelRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderPricingLineRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderPricingRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRequirementRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
class RepositoryPackageInputsProvider implements PackageInputsProvider {

    private final TenderRequirementRepository requirements;
    private final TenderPricingRepository pricing;
    private final TenderPricingLineRepository pricingLines;
    private final TenderPersonnelRepository personnel;

    @Override
    public PackageInputs current(TenantId tenantId, Tender t) {
        String details = PackageInputs.hashOf(List.of(
                Objects.toString(t.getName()), Objects.toString(t.getTenderAuthority()), Objects.toString(t.getAuthorityReferenceNumber()),
                Objects.toString(t.getClosingDate()), Objects.toString(t.getBriefingDate()), Objects.toString(t.getSiteInspectionDate()),
                Objects.toString(t.getEstimatedValue()), Objects.toString(t.getIndustry()), Objects.toString(t.getRequiredClassOfWork()),
                "requiresPricing=" + t.isRequiresPricing()));
        List<String> reqs = requirements.findByTender(tenantId, t.getId()).stream()
                .map(r -> r.getId() + "|" + r.getDescription() + "|" + r.getStatus()).toList();
        List<String> price = new ArrayList<>();
        pricing.findByTender(tenantId, t.getId()).ifPresent(p -> price.add("header|" + p.getUpdatedAt() + "|" + p.getCreatedAt()));
        pricingLines.findByTender(tenantId, t.getId()).forEach(l -> price.add(l.getId() + "|" + l.getUpdatedAt() + "|" + l.getCreatedAt()));
        List<String> people = personnel.findByTender(tenantId, t.getId()).stream()
                .map(p -> p.getId() + "|" + p.getEmployeeId() + "|" + p.getPersonType() + "|" + p.getExternalName() + "|" + p.getExternalOrganisation() + "|" + p.getRole()).toList();
        return new PackageInputs(details, PackageInputs.hashOf(reqs), PackageInputs.hashOf(price), PackageInputs.hashOf(people));
    }
}
