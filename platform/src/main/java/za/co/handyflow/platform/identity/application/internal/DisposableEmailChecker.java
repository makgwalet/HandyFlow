package za.co.handyflow.platform.identity.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Checks a registration email's domain against a curated list of
 * disposable/throwaway email providers. See disposable-email-domains.txt's
 * own header comment for maintenance notes and where this list came from.
 * <p>
 * WHY THIS EXISTS: registration previously validated only email format
 * (@Email on RegisterRequest), with no check against throwaway providers.
 * Combined with the auto-granted 60-day free pilot on signup (see
 * EmailTemplates' welcome-email copy: "Your 60-day free pilot has
 * started"), that's a real, live abuse vector — scripted signups with
 * disposable addresses can generate unlimited free trial windows. See
 * HandyFlow BOS Discovery doc, Section 19.2.
 * <p>
 * Fails open, not closed: if the resource file can't be loaded for any
 * reason, this logs a warning and treats every domain as non-disposable
 * rather than blocking all registration — a missing/misconfigured
 * blocklist should degrade to "no check," never to "nobody can sign up."
 */
@Slf4j
@Component
public class DisposableEmailChecker {

    private static final String RESOURCE_PATH = "disposable-email-domains.txt";

    private final Set<String> blockedDomains;

    public DisposableEmailChecker() {
        this.blockedDomains = Collections.unmodifiableSet(loadDomains());
        log.info("DisposableEmailChecker loaded {} blocked domains", blockedDomains.size());
    }

    public boolean isDisposable(String email) {
        if (email == null || !email.contains("@")) return false;
        String domain = email.substring(email.lastIndexOf('@') + 1).trim().toLowerCase();
        return blockedDomains.contains(domain);
    }

    private Set<String> loadDomains() {
        Set<String> domains = new HashSet<>();
        try (var input = new ClassPathResource(RESOURCE_PATH).getInputStream();
             var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim().toLowerCase();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                domains.add(trimmed);
            }
        } catch (Exception e) {
            log.warn("Could not load {} — disposable-email checking disabled, all domains " +
                            "treated as allowed (fail-open by design, not a bug): {}",
                    RESOURCE_PATH, e.getMessage());
        }
        return domains;
    }
}