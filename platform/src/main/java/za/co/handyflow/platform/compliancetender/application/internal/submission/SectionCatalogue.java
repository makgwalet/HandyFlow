package za.co.handyflow.platform.compliancetender.application.internal.submission;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The section types this build knows about, in default order. V1 is the six of ADR-005 decision 1; a
 * later type is one more entry here, not a change to the package service.
 */
public final class SectionCatalogue {

    public static final SectionType COVER_LETTER = new SectionType("COVER_LETTER", "Cover letter", 10, false);
    public static final SectionType COMPANY_PROFILE = new SectionType("COMPANY_PROFILE", "Company profile", 20, false);
    public static final SectionType COMPLIANCE = new SectionType("COMPLIANCE", "Compliance response", 30, false);
    public static final SectionType KEY_PERSONNEL = new SectionType("KEY_PERSONNEL", "Key personnel", 40, false);
    public static final SectionType PRICING = new SectionType("PRICING", "Pricing schedule", 50, true);
    public static final SectionType SUPPORTING_DOCUMENTS = new SectionType("SUPPORTING_DOCUMENTS", "Supporting documents", 60, false);

    private final Map<String, SectionType> byKey = new LinkedHashMap<>();

    public SectionCatalogue(List<SectionType> types) {
        for (SectionType type : types) {
            if (byKey.putIfAbsent(type.key(), type) != null) {
                throw new IllegalArgumentException("Section type " + type.key() + " is registered twice.");
            }
        }
    }

    public static SectionCatalogue v1() {
        return new SectionCatalogue(List.of(COVER_LETTER, COMPANY_PROFILE, COMPLIANCE, KEY_PERSONNEL, PRICING, SUPPORTING_DOCUMENTS));
    }

    public Optional<SectionType> find(String key) { return Optional.ofNullable(byKey.get(key)); }

    public List<SectionType> inDefaultOrder() {
        List<SectionType> all = new ArrayList<>(byKey.values());
        all.sort(Comparator.comparingInt(SectionType::defaultOrder));
        return List.copyOf(all);
    }
}
