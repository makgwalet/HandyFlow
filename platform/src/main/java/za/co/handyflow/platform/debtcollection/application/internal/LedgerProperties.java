package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** handyflow.debtcollection.ledger.allocate-fees-first (default false: payments go to principal unless the caller says otherwise). */
@Component
@ConfigurationProperties(prefix = "handyflow.debtcollection.ledger")
@Getter
@Setter
public class LedgerProperties {
    private boolean allocateFeesFirst = false;
}
