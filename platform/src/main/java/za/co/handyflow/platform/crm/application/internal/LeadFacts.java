package za.co.handyflow.platform.crm.application.internal;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import za.co.handyflow.platform.crm.domain.model.Customer;

/** Reads the facts a rule looks at off a lead. */
final class LeadFacts {
    private LeadFacts() {}

    static LeadConditions.Facts of(Customer c) {
        Set<String> tags = new HashSet<>();
        if (c.getTags() != null) for (String t : c.getTags()) tags.add(t.toLowerCase(Locale.ROOT));
        return new LeadConditions.Facts(c.getLeadSource(), c.getPipelineStage(), c.getDealValue(),
                c.getEmail() != null && !c.getEmail().isBlank(), c.getPhone() != null && !c.getPhone().isBlank(), c.getOwnerId() != null, tags);
    }
}
