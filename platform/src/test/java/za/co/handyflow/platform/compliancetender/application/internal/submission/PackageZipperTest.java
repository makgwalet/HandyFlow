package za.co.handyflow.platform.compliancetender.application.internal.submission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PackageZipperTest {

    @Test
    @DisplayName("names are numbered, cleaned and keep a lower-case extension")
    void names() {
        assertThat(PackageZipper.numberedName(3, "Tax Clearance (2026).PDF")).isEqualTo("03-tax-clearance-2026.pdf");
        assertThat(PackageZipper.numberedName(12, "01-cover-letter.pdf")).isEqualTo("12-01-cover-letter.pdf");
        assertThat(PackageZipper.numberedName(1, "noextension")).isEqualTo("01-noextension");
    }

    @Test
    @DisplayName("the ZIP holds every entry in order with its bytes")
    void zips() throws Exception {
        byte[] zip = PackageZipper.zip(List.of(new PackageZipper.Entry("01-a.pdf", new byte[]{1, 2}), new PackageZipper.Entry("02-b.xlsx", new byte[]{3})));
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) { names.add(e.getName() + ":" + in.readAllBytes().length); }
        }
        assertThat(names).containsExactly("01-a.pdf:2", "02-b.xlsx:1");
    }
}
