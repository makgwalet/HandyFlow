package za.co.handyflow.platform.capture.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OcrEngineTest {
    @Test
    void onlyKnownLanguageCodesAreAccepted() {
        assertEquals("eng", OcrEngine.validLanguages("eng"));
        assertEquals("eng+afr", OcrEngine.validLanguages(" eng+afr "));
        assertEquals("eng", OcrEngine.validLanguages("eng; rm -rf /"));
        assertEquals("eng", OcrEngine.validLanguages("../x"));
        assertEquals("eng", OcrEngine.validLanguages(null));
    }

    @Test
    void doesNothingWhenSwitchedOff() {
        assertEquals("", new OcrEngine(false, "tesseract", "pdftoppm", "eng", 10).read(new byte[]{1, 2, 3}, false));
    }
}
