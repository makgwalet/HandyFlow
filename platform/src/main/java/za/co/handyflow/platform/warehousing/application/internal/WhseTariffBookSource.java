package za.co.handyflow.platform.warehousing.application.internal;

import java.util.UUID;

/** Where the accrual job gets a warehouse's tariff versions from (a seam so the job can be tested without a database). */
interface WhseTariffBookSource {
    WhseTariffBook bookFor(UUID tenantId);
}
