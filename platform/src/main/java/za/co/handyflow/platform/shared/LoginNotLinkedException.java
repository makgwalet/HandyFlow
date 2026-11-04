package za.co.handyflow.platform.shared;

import org.springframework.http.HttpStatus;

/** A signed-in user who has no employee record, asking for a "my own" screen. Says it plainly, without internal ids. */
public class LoginNotLinkedException extends HandyFlowException {

    public static final String MESSAGE = "Your login is not linked to an employee record yet. Ask HR to link it.";

    public LoginNotLinkedException() {
        super(MESSAGE, HttpStatus.NOT_FOUND, "LOGIN_NOT_LINKED");
    }
}
