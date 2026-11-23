package za.co.handyflow.platform.shared;

import jakarta.servlet.ServletException;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * The order of things in the JWT filter, kept apart from the servlet types so it can be tested directly.
 *
 * <ol>
 *   <li>Try to authenticate. If that fails for any reason (a malformed token, say) the request carries on <b>unauthenticated</b> and
 *       whatever was half-set is cleared, so the rules further down answer 401/403 as they would for no token.</li>
 *   <li>Run the rest of the chain <b>exactly once</b>. An exception from the chain (the controller, other filters) is not caught here:
 *       the old code caught it and ran the chain a second time, which could repeat a write.</li>
 *   <li>Always clear the tenant and security context afterwards, because the thread goes back to the pool.</li>
 * </ol>
 */
final class JwtFilterFlow {

    @FunctionalInterface
    interface Chain { void proceed() throws ServletException, IOException; }

    private JwtFilterFlow() {}

    static void run(Runnable authenticate, Consumer<Exception> onAuthFailure, Runnable clear, Chain chain)
            throws ServletException, IOException {
        try {
            try {
                authenticate.run();
            } catch (Exception e) {
                onAuthFailure.accept(e);
                clear.run();
            }
            chain.proceed();
        } finally {
            clear.run();
        }
    }
}
