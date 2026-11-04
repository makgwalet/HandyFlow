package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class LoginNotLinkedExceptionTest {

    @Test
    void saysItPlainlyWithNoInternalIdAndAStableCode() {
        var e = new LoginNotLinkedException();
        assertThat(e.getMessage()).isEqualTo("Your login is not linked to an employee record yet. Ask HR to link it.");
        assertThat(e.getMessage()).doesNotContainPattern("[0-9a-f]{8}-[0-9a-f]{4}");
        assertThat(e.getErrorCode()).isEqualTo("LOGIN_NOT_LINKED");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
