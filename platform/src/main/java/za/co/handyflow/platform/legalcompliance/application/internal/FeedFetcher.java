package za.co.handyflow.platform.legalcompliance.application.internal;

import java.io.IOException;

/** Fetches a feed's bytes. An interface so tests (and any future egress-proxy implementation) can replace the real HTTP call. The message of an IOException is shown to users, so keep it free of internals. */
public interface FeedFetcher {
    byte[] fetch(String url) throws IOException;
}
