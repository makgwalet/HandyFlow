package za.co.handyflow.platform.compliancetender.application.internal.submission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PackageInputsTest {

    @Test
    @DisplayName("hashOf ignores the order of the lines but notices a changed, added or removed line")
    void hashOrderIndependent() {
        String a = PackageInputs.hashOf(List.of("x", "y", "z"));
        assertThat(PackageInputs.hashOf(List.of("z", "x", "y"))).isEqualTo(a);
        assertThat(PackageInputs.hashOf(List.of("x", "y"))).isNotEqualTo(a);
        assertThat(PackageInputs.hashOf(List.of("x", "y", "z2"))).isNotEqualTo(a);
        assertThat(a).hasSize(12);
        assertThat(PackageInputs.hashOf(List.of())).isNotEqualTo(a);
    }

    @Test
    @DisplayName("encode and parse round-trip; garbage and null parse to null")
    void roundTrip() {
        PackageInputs in = new PackageInputs("aa", "bb", "cc", "dd");
        assertThat(PackageInputs.parse(in.encode())).isEqualTo(in);
        assertThat(PackageInputs.parse(null)).isNull();
        assertThat(PackageInputs.parse("")).isNull();
        assertThat(PackageInputs.parse("nonsense")).isNull();
        assertThat(PackageInputs.parse("details=a;requirements=b")).isNull();
    }

    @Test
    @DisplayName("changes names each part that differs and says nothing when unchanged or unknown")
    void changes() {
        PackageInputs built = new PackageInputs("d", "r", "p", "k");
        assertThat(PackageInputs.changes(built, built)).isEmpty();
        assertThat(PackageInputs.changes(null, built)).isEmpty();
        assertThat(PackageInputs.changes(built, new PackageInputs("d", "r2", "p", "k2")))
                .containsExactly("The requirements changed", "The key personnel changed");
    }
}
