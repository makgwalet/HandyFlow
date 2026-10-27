package za.co.handyflow.platform.facilities.application.internal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * Runs one sync operation in its own transaction, so a rejected operation rolls back only itself and the rest of
 * the batch still goes through. Kept as a separate bean so the transaction proxy applies.
 */
@Component
public class FacilitySyncExecutor {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T inNewTransaction(Supplier<T> work) {
        return work.get();
    }
}
