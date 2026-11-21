package za.co.handyflow.platform.security.application.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class PinRulesTest {
    @Test void weakPins() {
        for (String p : new String[]{"000000","111111","123456","654321","234567","987654","121212","123123","112233","abc123","12345",null})
            assertTrue(PinRules.weak(p), String.valueOf(p));
    }
    @Test void strongPins() {
        for (String p : new String[]{"483920","750194","294857"}) assertFalse(PinRules.weak(p), p);
    }
    @Test void generatedPinsAreNeverWeak() {
        SecureRandom r = new SecureRandom();
        for (int i = 0; i < 5000; i++) { String p = PinRules.generate(r); assertEquals(6, p.length()); assertFalse(PinRules.weak(p), p); }
    }
}
