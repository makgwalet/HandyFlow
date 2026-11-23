package za.co.handyflow.platform.agriculture.application.internal;

import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * How an edit request changes a record. A field that is left out of the request keeps its value;
 * a field with a value sets it; a field named in the request's "clear" list is emptied. Before this,
 * a field left out of the request was emptied, so editing one note wiped the sire, dam, dates and
 * paddock.
 */
final class AgPatchRules {

    static final Set<String> ANIMAL_CLEARABLE = Set.of(
            "productionAreaId", "enterpriseId", "name", "breed", "dateOfBirth", "sireId", "damId", "notes");
    static final Set<String> GROUP_CLEARABLE = Set.of("productionAreaId", "enterpriseId", "breed", "notes");

    private AgPatchRules() {}

    /** The fields to empty. An unknown name is refused so a typo cannot silently do nothing. */
    static Set<String> clearSet(List<String> clear, Set<String> allowed) {
        Set<String> out = new HashSet<>();
        if (clear == null) return out;
        for (String f : clear) {
            if (f == null || !allowed.contains(f))
                throw new HandyFlowException("Field '" + f + "' cannot be cleared", HttpStatus.BAD_REQUEST, "INVALID_CLEAR_FIELD");
            out.add(f);
        }
        return out;
    }

    /** Naming a field to clear and also giving it a value is a contradiction, so it is refused. */
    static <T> T apply(String field, T current, T incoming, Set<String> clear) {
        boolean hasValue = incoming != null && !(incoming instanceof String s && s.isBlank());
        if (clear.contains(field)) {
            if (hasValue)
                throw new HandyFlowException("Field '" + field + "' was given a value and also marked to clear", HttpStatus.BAD_REQUEST, "CONFLICTING_FIELD");
            return null;
        }
        if (!hasValue) return current;
        return incoming instanceof String s ? castString(s.trim()) : incoming;
    }

    @SuppressWarnings("unchecked")
    private static <T> T castString(String s) { return (T) s; }
}
