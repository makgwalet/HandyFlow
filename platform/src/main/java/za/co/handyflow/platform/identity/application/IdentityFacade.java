package za.co.handyflow.platform.identity.application;

import za.co.handyflow.platform.identity.dto.request.LoginRequest;
import za.co.handyflow.platform.identity.dto.request.RegisterRequest;
import za.co.handyflow.platform.identity.dto.response.AuthResponse;

public interface IdentityFacade {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
