package za.co.handyflow.platform.crm.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.crm.domain.model.WebhookEvent;

class WebhookRulesTest {
    private static void refuses(String name, String url, List<WebhookEvent> events, String msg) {
        assertThatThrownBy(() -> WebhookRules.check(name, url, events)).isInstanceOf(IllegalArgumentException.class).hasMessage(msg);
    }
    private static boolean blocked(String ip) throws Exception { return WebhookRules.isBlocked(InetAddress.getByName(ip)); }

    @Test
    void aGoodWebhookPasses() {
        WebhookRules.check("CRM sync", "https://hooks.example.com/handyflow?x=1", List.of(WebhookEvent.LEAD_CREATED, WebhookEvent.STAGE_CHANGED));
        WebhookRules.check("By IP", "https://8.8.8.8/hook", List.of(WebhookEvent.OPPORTUNITY_WON));
    }

    @Test
    void theNameEventsAndAddressAreChecked() {
        List<WebhookEvent> ok = List.of(WebhookEvent.LEAD_CREATED);
        refuses(" ", "https://a.example.com", ok, "Name the webhook");
        refuses("x".repeat(121), "https://a.example.com", ok, "The name can be at most 120 characters");
        refuses("x", "https://a.example.com", List.of(), "Choose at least one event");
        refuses("x", "https://a.example.com", List.of(WebhookEvent.PING), "Ping is only used by the test button");
        refuses("x", " ", ok, "Give the address to send to");
        refuses("x", "http://a.example.com", ok, "The address must start with https://");
        refuses("x", "ftp://a.example.com", ok, "The address must start with https://");
        refuses("x", "https://user:pw@a.example.com", ok, "The address cannot contain a user name or password");
        refuses("x", "https://", ok, "That address is not valid");
        refuses("x", "https://a.example.com/" + "p".repeat(500), ok, "The address can be at most 500 characters");
    }

    @Test
    void addressesInsideANetworkAreRefused() {
        List<WebhookEvent> ok = List.of(WebhookEvent.LEAD_CREATED);
        String inside = "That address points inside a private network";
        for (String url : List.of("https://localhost/x", "https://app.localhost/x", "https://printer.local/x", "https://db.internal/x", "https://intranet/x",
                "https://127.0.0.1/x", "https://10.1.2.3/x", "https://192.168.0.5/x", "https://172.16.0.1/x", "https://169.254.169.254/latest/meta-data", "https://[::1]/x", "https://[fd00::1]/x")) {
            refuses("x", url, ok, inside);
        }
    }

    @Test
    void blockedAddressRanges() throws Exception {
        for (String ip : List.of("127.0.0.1", "10.0.0.1", "172.31.255.255", "192.168.1.1", "169.254.169.254", "100.64.0.1", "100.127.255.255", "0.0.0.0", "224.0.0.1", "::1", "fe80::1", "fd12:3456::1")) {
            assertThat(blocked(ip)).as(ip).isTrue();
        }
        for (String ip : List.of("8.8.8.8", "1.1.1.1", "100.63.255.255", "100.128.0.1", "172.32.0.1", "2606:4700:4700::1111")) {
            assertThat(blocked(ip)).as(ip).isFalse();
        }
    }

    @Test
    void messagesAreSignedWithHmacSha256() {
        // RFC 4231 test case 2: key "Jefe", data "what do ya want for nothing?"
        assertThat(WebhookRules.sign("Jefe", "what do ya want for nothing?")).isEqualTo("sha256=5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
        assertThat(WebhookRules.sign("a", "body")).isNotEqualTo(WebhookRules.sign("b", "body"));
    }

    @Test
    void eventsRoundTripAndAnswersAreWorded() {
        assertThat(WebhookRules.joinEvents(List.of(WebhookEvent.LEAD_CREATED, WebhookEvent.LEAD_CREATED, WebhookEvent.OPPORTUNITY_WON))).isEqualTo("LEAD_CREATED,OPPORTUNITY_WON");
        assertThat(WebhookRules.parseEvents("LEAD_CREATED, STAGE_CHANGED")).containsExactly(WebhookEvent.LEAD_CREATED, WebhookEvent.STAGE_CHANGED);
        assertThat(WebhookRules.parseEvents(null)).isEmpty();
        assertThat(WebhookRules.statusText(204)).isEqualTo("Delivered (204)");
        assertThat(WebhookRules.statusText(301)).contains("redirects are not followed");
        assertThat(WebhookRules.statusText(403)).contains("refused");
        assertThat(WebhookRules.statusText(404)).contains("not found");
        assertThat(WebhookRules.statusText(503)).contains("error");
        assertThat(WebhookRules.statusText(418)).isEqualTo("The receiver answered 418");
    }
}
