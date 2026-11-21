package za.co.handyflow.platform.security.application.internal;

import java.security.SecureRandom;
import java.util.Set;

/** Rules for guard PINs (6 digits). Pure. */
final class PinRules {

    private static final Set<String> COMMON = Set.of(
            "123456", "654321", "123123", "112233", "121212", "696969", "159753", "123321", "010203", "000000", "111111");

    private PinRules() {}

    /** True for PINs that are guessed first: repeated, a run (up or down), a repeated pair or triple, or a common one. */
    static boolean weak(String pin) {
        if (pin == null || !pin.matches("\\d{6}")) return true;
        if (COMMON.contains(pin)) return true;
        boolean same = true, up = true, down = true;
        for (int i = 1; i < 6; i++) {
            int d = pin.charAt(i) - pin.charAt(i - 1);
            if (d != 0) same = false;
            if (d != 1) up = false;
            if (d != -1) down = false;
        }
        if (same || up || down) return true;
        return pin.substring(0, 3).equals(pin.substring(3)) || pin.charAt(0) == pin.charAt(2) && pin.charAt(2) == pin.charAt(4)
                && pin.charAt(1) == pin.charAt(3) && pin.charAt(3) == pin.charAt(5);
    }

    /** A random 6-digit PIN that is not weak. */
    static String generate(SecureRandom random) {
        String pin;
        do {
            pin = String.format("%06d", random.nextInt(1_000_000));
        } while (weak(pin));
        return pin;
    }
}
