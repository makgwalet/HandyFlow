package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.LetterheadRules.Letterhead;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.handyflow.platform.hr.domain.model.LetterheadRules.*;

class LetterheadRulesTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

    private static String uri(String mime, byte[] bytes) { return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes); }

    @Test
    void theAddressIsOneLineAndLeavesOutWhatIsMissing() {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("street", "12 Mining Road"); a.put("suburb", "Germiston"); a.put("city", "Johannesburg"); a.put("province", "Gauteng"); a.put("postalCode", "1401");
        assertThat(addressLine(a)).isEqualTo("12 Mining Road, Germiston, Johannesburg, Gauteng, 1401");
        Map<String, String> partial = new LinkedHashMap<>();
        partial.put("street", "1 Main St"); partial.put("suburb", " "); partial.put("city", "Pretoria");
        assertThat(addressLine(partial)).isEqualTo("1 Main St, Pretoria");
        assertThat(addressLine(Map.of())).isNull();
        assertThat(addressLine(null)).isNull();
    }

    @Test
    void theContactLineNamesWhatThereIs() {
        assertThat(contactLine("011 555 0200", "hr@example.com")).isEqualTo("Tel 011 555 0200 · hr@example.com");
        assertThat(contactLine(null, "hr@example.com")).isEqualTo("hr@example.com");
        assertThat(contactLine(" ", null)).isNull();
    }

    @Test
    void theHeadHasNameLinesAndOnlyAUsableLogo() {
        Letterhead h = of("Zeta Earthmoving", Map.of("city", "Johannesburg"), "011 555 0200", null, "4560123456", uri("image/png", PNG));
        assertThat(h.name()).isEqualTo("Zeta Earthmoving");
        assertThat(h.lines()).containsExactly("Johannesburg", "Tel 011 555 0200", "VAT no. 4560123456");
        assertThat(h.hasLogo()).isTrue();
        Letterhead bare = of("Zeta", null, null, null, " ", null);
        assertThat(bare.lines()).isEmpty();
        assertThat(bare.hasLogo()).isFalse();
        assertThat(Letterhead.plain("X").lines()).isEmpty();
    }

    @Test
    void aLogoIsAcceptedOnlyWhenItReallyIsAPngOrJpegOfSensibleSize() {
        assertThat(java.util.Arrays.equals(decodeLogo(uri("image/png", PNG)), PNG)).isTrue();
        assertThat(java.util.Arrays.equals(decodeLogo(uri("image/jpeg", JPG)), JPG)).isTrue();
        assertThat(java.util.Arrays.equals(decodeLogo(uri("image/jpg", JPG)), JPG)).isTrue();
        assertThat(decodeLogo(uri("image/png", JPG))).isNull();           // says PNG, is a JPEG
        assertThat(decodeLogo(uri("image/jpeg", PNG))).isNull();
        assertThat(decodeLogo(uri("image/svg+xml", "<svg/>".getBytes()))).isNull();
        assertThat(decodeLogo(uri("image/gif", PNG))).isNull();
        assertThat(decodeLogo("data:image/png;base64,@@@not base64@@@")).isNull();
        assertThat(decodeLogo("https://example.com/logo.png")).isNull();
        assertThat(decodeLogo("data:image/png;base64,")).isNull();
        assertThat(decodeLogo(null)).isNull();
        byte[] big = new byte[MAX_LOGO_BYTES + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertThat(decodeLogo(uri("image/png", big))).isNull();
    }
}
