package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.invoicing.domain.model.Quote;
import za.co.handyflow.platform.invoicing.domain.repository.QuoteRepository;
import za.co.handyflow.platform.invoicing.dto.LineItemResponse;
import za.co.handyflow.platform.invoicing.dto.PublicQuoteView;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;

import java.util.UUID;

/**
 * Backs the PUBLIC, unauthenticated accept/reject link sent in quote
 * emails. Every method here is reachable by anyone holding a valid token —
 * NOT gated by @PreAuthorize, NOT tenant-scoped by an authenticated
 * principal. The token itself is the credential.
 *
 * Deliberately kept separate from QuoteService (the authenticated,
 * staff-facing service) rather than adding public methods there — makes it
 * obvious at a glance which methods are safe to expose without
 * authentication, and prevents an accidental future addition to
 * QuoteService from being reachable by an unauthenticated caller by
 * mistake.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotePublicAccessService {

    private final QuoteRepository quoteRepository;
    private final TenantFacade tenantFacade;
    private final StaffNotifier staffNotifier;

    @Transactional(readOnly = true)
    public PublicQuoteView getByToken(UUID token) {
        Quote quote = findByToken(token);
        return toView(quote);
    }

    @Transactional
    public PublicQuoteView acceptByToken(UUID token) {
        Quote quote = findByToken(token);
        if (!quote.isPubliclyActionable()) {
            throw new IllegalStateException("This quote can no longer be actioned online.");
        }
        quote.accept();
        quoteRepository.save(quote);
        log.info("Quote={} accepted via public link", quote.getId());

        staffNotifier.notify(quote.getTenantId(),
                NotificationType.QUOTE_ACCEPTED,
                "Quote accepted online: " + quote.getQuoteNumber(),
                "The client accepted quote " + quote.getQuoteNumber() + " via the emailed link.",
                "/quotes/" + quote.getId(), quote.getId().toString());

        return toView(quote);
    }

    @Transactional
    public PublicQuoteView rejectByToken(UUID token) {
        Quote quote = findByToken(token);
        if (!quote.isPubliclyActionable()) {
            throw new IllegalStateException("This quote can no longer be actioned online.");
        }
        quote.reject();
        quoteRepository.save(quote);
        log.info("Quote={} rejected via public link", quote.getId());

        staffNotifier.notify(quote.getTenantId(),
                NotificationType.QUOTE_REJECTED,
                "Quote rejected online: " + quote.getQuoteNumber(),
                "The client rejected quote " + quote.getQuoteNumber() + " via the emailed link.",
                "/quotes/" + quote.getId(), quote.getId().toString());

        return toView(quote);
    }

    private Quote findByToken(UUID token) {
        return quoteRepository.findByPublicAccessToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", "link"));
    }

    private PublicQuoteView toView(Quote q) {
        var lineItems = q.getLineItems().stream()
                .map(li -> new LineItemResponse(
                        li.getId(), li.getCatalogueItemId(),
                        li.getDescription(), li.getUnit(),
                        li.getQuantity(), li.getUnitPrice(),
                        li.getVatRate(), li.getLineTotal(), li.getVatAmount()
                )).toList();

        String companyName = tenantFacade.findTenantDetails(q.getTenantId())
                .map(t -> t.companyName()).orElse("");

        return new PublicQuoteView(
                q.getQuoteNumber(), q.getStatus().name(), companyName, q.getTitle(),
                q.getSubtotal(), q.getVatTotal(), q.getTotal(), q.getCurrency(),
                q.getExpiresAt(), lineItems, q.isPubliclyActionable()
        );
    }
}