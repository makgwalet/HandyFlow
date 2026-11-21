package za.co.handyflow.platform.security.application.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Pure rules for guard login. */
final class LoginRules {

    enum PickKind { NONE, ONE, AMBIGUOUS }

    record Pick<T>(PickKind kind, T guard) {}

    private LoginRules() {}

    /**
     * Phone numbers are not unique across tenants: a guard registered with two companies has one phone and two accounts.
     * With one candidate nothing changes. With several, the PIN decides which account is meant; if the PIN fits none the
     * login simply fails (and no account is penalised, since we cannot tell whose attempt it was), and if it fits more than
     * one the guard must use their employee code.
     */
    static <T> Pick<T> pickByPhone(List<T> candidates, Predicate<T> pinMatches) {
        if (candidates.isEmpty()) return new Pick<>(PickKind.NONE, null);
        if (candidates.size() == 1) return new Pick<>(PickKind.ONE, candidates.get(0));
        List<T> hits = new ArrayList<>();
        for (T c : candidates) if (pinMatches.test(c)) hits.add(c);
        if (hits.isEmpty()) return new Pick<>(PickKind.NONE, null);
        return hits.size() == 1 ? new Pick<>(PickKind.ONE, hits.get(0)) : new Pick<>(PickKind.AMBIGUOUS, null);
    }

    /**
     * A locked account stops everyone, except a request from the guard's own enrolled device. Without this, anyone who
     * knows a phone number can lock that guard out at shift change by typing wrong PINs.
     */
    static boolean lockBlocks(boolean locked, String registeredDeviceId, String requestDeviceId) {
        if (!locked) return false;
        return registeredDeviceId == null || registeredDeviceId.isBlank() || !registeredDeviceId.equals(requestDeviceId);
    }
}
