package za.co.handyflow.platform.legalcompliance.application.internal;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Real HTTP fetch with the SSRF guards from {@link UrlRules}: https only, every resolved address must be public, redirects are not followed, the body is size-capped and timeouts are short.
 * Residual risk: the name is resolved here for the check and again by the HTTP client, so a hostile DNS server could answer differently the second time (DNS rebinding). If that matters
 * for your threat model, route this call through an egress proxy that enforces the same rule; this class is the single place to do it.
 */
@Component
public class HttpFeedFetcher implements FeedFetcher {

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public byte[] fetch(String url) throws IOException {
        URI uri;
        try { uri = URI.create(UrlRules.validate(url)); } catch (IllegalArgumentException e) { throw new IOException(e.getMessage()); }
        try {
            for (InetAddress a : InetAddress.getAllByName(uri.getHost()))
                if (!UrlRules.isPublicAddress(a)) throw new IOException("That host name does not resolve to a public address");
        } catch (UnknownHostException e) {
            throw new IOException("The host could not be found");
        }
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9")
                .header("User-Agent", "HandyFlow-RegulatoryWatch/1.0").GET().build();
        try {
            HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = res.body()) {
                int code = res.statusCode();
                if (code >= 300 && code < 400) throw new IOException("The server redirected the request. Redirects are not followed; use the feed's final address");
                if (code != 200) throw new IOException("The server answered HTTP " + code);
                byte[] body = in.readNBytes(FeedRules.MAX_BYTES + 1);
                if (body.length > FeedRules.MAX_BYTES) throw new IOException("The feed is larger than " + (FeedRules.MAX_BYTES / 1024) + " KB");
                return body;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("The fetch was interrupted");
        } catch (IOException e) {
            if (e.getMessage() != null && (e.getMessage().startsWith("The ") || e.getMessage().startsWith("That "))) throw e;
            throw new IOException("The feed could not be reached");
        }
    }
}
