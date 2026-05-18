package za.co.handyflow.platform.invoicing.domain.model;

import java.util.Set;

public enum QuoteStatus {
    DRAFT {
        public Set<QuoteStatus> allowedTransitions() {
            return Set.of(SENT);
        }
    },
    SENT {
        public Set<QuoteStatus> allowedTransitions() {
            return Set.of(ACCEPTED, REJECTED, EXPIRED);
        }
    },
    ACCEPTED {
        public Set<QuoteStatus> allowedTransitions() {
            return Set.of(INVOICED);
        }
    },
    REJECTED {
        public Set<QuoteStatus> allowedTransitions() {
            return Set.of();
        }
    },
    EXPIRED {
        public Set<QuoteStatus> allowedTransitions() {
            return Set.of();
        }
    },
    INVOICED {
        public Set<QuoteStatus> allowedTransitions() {
            return Set.of();
        }
    };

    public abstract Set<QuoteStatus> allowedTransitions();

    public boolean canTransitionTo(QuoteStatus target) {
        return allowedTransitions().contains(target);
    }
}
