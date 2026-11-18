package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.handyflow.platform.contracting.domain.model.ContractSigningToken;
import za.co.handyflow.platform.contracting.domain.repository.ContractSigningTokenRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SigningTokenServiceTest {

    @Mock ContractSigningTokenRepository repo;
    @InjectMocks SigningTokenService service;

    UUID party = UUID.randomUUID(), contract = UUID.randomUUID(), tenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "signingSecret", "test-secret-test-secret-test-secret");
        ReflectionTestUtils.setField(service, "baseUrl", "https://app.example");
        ReflectionTestUtils.setField(service, "tokenValidityHours", 72);
    }

    /** Stores the token the way the real code does (hash only) and makes the repository find it by hash. */
    ContractSigningToken stored(String token) {
        ContractSigningToken st = ContractSigningToken.create(tenant, contract, party, token, Instant.now().plus(72, ChronoUnit.HOURS), null);
        lenient().when(repo.findByTokenHash(service.sha256(token))).thenReturn(Optional.of(st));
        return st;
    }

    String tamper(String token, java.util.function.UnaryOperator<String> change) {
        String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(change.apply(decoded).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void aFreshStoredTokenValidatesAndCarriesItsClaims() {
        String token = service.generateToken(contract, party, tenant);
        stored(token);
        SigningTokenService.SigningClaims c = service.validateToken(token);
        assertEquals(party, c.partyId());
        assertEquals(contract, c.contractId());
        assertEquals(tenant, c.tenantId());
        assertTrue(c.expiresAt().isAfter(Instant.now()));
    }

    @Test
    void urlContainsTheTokenUnderSignPath() {
        String url = service.generateSigningUrl(contract, party, tenant);
        assertTrue(url.startsWith("https://app.example/sign/"));
    }

    @Test
    void everyTokenIsDifferent() {
        assertNotEquals(service.generateToken(contract, party, tenant), service.generateToken(contract, party, tenant));
    }

    @Test
    void tamperingWithAnyClaimIsRejected() {
        String token = service.generateToken(contract, party, tenant);
        stored(token);
        String otherTenant = tamper(token, d -> d.replace(tenant.toString(), UUID.randomUUID().toString()));
        stored(otherTenant);
        assertThrows(IllegalArgumentException.class, () -> service.validateToken(otherTenant));
        String otherParty = tamper(token, d -> d.replace(party.toString(), UUID.randomUUID().toString()));
        stored(otherParty);
        assertThrows(IllegalArgumentException.class, () -> service.validateToken(otherParty));
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejected() {
        String token = service.generateToken(contract, party, tenant);
        stored(token);
        ReflectionTestUtils.setField(service, "signingSecret", "a-different-secret-a-different-secret");
        assertThrows(IllegalArgumentException.class, () -> service.validateToken(token));
    }

    @Test
    void anExpiredTokenIsRejectedBeforeTheDatabaseIsConsulted() {
        ReflectionTestUtils.setField(service, "tokenValidityHours", -1);
        String token = service.generateToken(contract, party, tenant);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.validateToken(token));
        assertTrue(e.getMessage().toLowerCase().contains("expired"));
    }

    @Test
    void aTokenWithNoStoredRecordIsRejected() {
        String token = service.generateToken(contract, party, tenant);
        lenient().when(repo.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.validateToken(token));
    }

    @Test
    void aRevokedTokenIsRejected() {
        String token = service.generateToken(contract, party, tenant);
        ContractSigningToken st = stored(token);
        ReflectionTestUtils.setField(st, "revokedAt", Instant.now());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.validateToken(token));
        assertTrue(e.getMessage().toLowerCase().contains("revoked"));
    }

    @Test
    void aUsedTokenIsRejected() {
        String token = service.generateToken(contract, party, tenant);
        ContractSigningToken st = stored(token);
        ReflectionTestUtils.setField(st, "usedAt", Instant.now());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.validateToken(token));
        assertTrue(e.getMessage().toLowerCase().contains("already been used"));
    }

    @Test
    void garbageIsRejectedAsInvalidNotAsAServerError() {
        for (String bad : new String[]{"", "not-base64!!", Base64.getUrlEncoder().withoutPadding().encodeToString("a:b:c".getBytes(StandardCharsets.UTF_8))})
            assertThrows(IllegalArgumentException.class, () -> service.validateToken(bad));
    }

    @Test
    void theStoredRecordKeepsOnlyAHashNeverTheToken() {
        String token = service.generateToken(contract, party, tenant);
        ContractSigningToken st = stored(token);
        assertNull(st.getToken());
        assertEquals(service.sha256(token), st.getTokenHash());
        assertEquals(64, st.getTokenHash().length());
    }

    @Test
    void sha256IsStable() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", service.sha256("abc"));
    }
}
