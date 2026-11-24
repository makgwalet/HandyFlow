package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientProfileRulesTest {

    @Test @DisplayName("blank values are treated as not given")
    void blanks() {
        assertThat(ClientProfileRules.registrationNumber("  ")).isNull();
        assertThat(ClientProfileRules.taxNumber(null)).isNull();
        assertThat(ClientProfileRules.requireOneOf("entityType", "", ClientProfileRules.ENTITY_TYPES)).isNull();
        assertThat(ClientProfileRules.clean("   ", 10)).isNull();
    }

    @Test @DisplayName("registration number loses spaces and is upper-cased")
    void registrationNumber() {
        assertThat(ClientProfileRules.registrationNumber(" 2015 / 123456 / 07 ")).isEqualTo("2015/123456/07");
        assertThatThrownBy(() -> ClientProfileRules.registrationNumber("12")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClientProfileRules.registrationNumber("2015;DROP")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("tax numbers are ten digits, VAT numbers start with 4")
    void taxAndVat() {
        assertThat(ClientProfileRules.taxNumber("9012 345 678")).isEqualTo("9012345678");
        assertThat(ClientProfileRules.vatNumber("4123456789")).isEqualTo("4123456789");
        assertThatThrownBy(() -> ClientProfileRules.taxNumber("12345")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClientProfileRules.vatNumber("9012345678")).hasMessageContaining("starting with 4");
    }

    @Test @DisplayName("choices are upper-cased and unknown ones are refused")
    void choices() {
        assertThat(ClientProfileRules.requireOneOf("riskRating", "high", ClientProfileRules.RISK_RATINGS)).isEqualTo("HIGH");
        assertThatThrownBy(() -> ClientProfileRules.requireOneOf("riskRating", "EXTREME", ClientProfileRules.RISK_RATINGS))
                .hasMessageContaining("riskRating");
    }

    @Test @DisplayName("employee count must be sensible")
    void employees() {
        assertThat(ClientProfileRules.employeeCount(0)).isEqualTo(0);
        assertThat(ClientProfileRules.employeeCount(null)).isNull();
        assertThatThrownBy(() -> ClientProfileRules.employeeCount(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("long text is cut to its limit")
    void clean() {
        assertThat(ClientProfileRules.clean("  abcdef ", 3)).isEqualTo("abc");
    }
}
