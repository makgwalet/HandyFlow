package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DownloadNamesTest {
    @Test
    void anOrdinaryNameIsKept() { assertEquals("Revenue_Schedule v2.xlsx", DownloadNames.safe("Revenue_Schedule v2.xlsx")); }

    @Test
    void quotesSlashesAndLineBreaksAreRemovedSoTheHeaderCannotBeBroken() {
        assertEquals("a.pdfX-Evil: 1", DownloadNames.safe("a.pdf\"\r\nX-Evil: 1"));
        assertEquals("..etcpasswd", DownloadNames.safe("../etc/passwd"));
        assertEquals("evil.exe", DownloadNames.safe("c:\\temp\\evil.exe".replace("c:\\temp\\", "")));
        assertFalse(DownloadNames.safe("x\"y\\z/w").matches(".*[\"\\\\/].*"));
    }

    @Test
    void nothingLeftBecomesTheFallbackAndLongNamesAreCut() {
        assertEquals("workpaper", DownloadNames.safe(null));
        assertEquals("workpaper", DownloadNames.safe("   "));
        assertEquals("workpaper", DownloadNames.safe("\"\"\r\n"));
        assertEquals(200, DownloadNames.safe("a".repeat(500)).length());
    }
}
