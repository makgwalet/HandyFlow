package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * handyflow.debtcollection.legal.public-holidays — ISO dates (e.g. 2026-12-25) excluded from the advisory
 * business-day calculations on legal notices. Empty by default; the advisory output says so, because weekday-only
 * counting can understate the true elapsed period.
 */
@Component
@ConfigurationProperties(prefix = "handyflow.debtcollection.legal")
@Getter
@Setter
public class LegalProperties {
    private List<LocalDate> publicHolidays = new ArrayList<>();
}
