package za.co.handyflow.platform.internalaudit.application.internal;

import za.co.handyflow.platform.internalaudit.domain.model.AuditInvestigationLogEntry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** The hash chain behind a case record. Pure, so it can be checked without a database. */
public final class InvestigationChain {
    private InvestigationChain() {}
    public static final String GENESIS = "0".repeat(64);

    /** Time is hashed as seconds and nanoseconds so the value is the same after a database round trip. */
    public static String hash(int seq, Instant at, UUID actorId, String action, String detail, String prevHash) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String s = seq + "\u0000" + at.getEpochSecond() + "." + at.getNano() + "\u0000" + actorId + "\u0000" + action + "\u0000" + (detail == null ? "" : detail) + "\u0000" + prevHash;
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public record Result(boolean ok, int entries, Integer brokenAtSeq) {}

    /** Recomputes every line from the start. The first line that does not match (wrong order, wrong link, wrong hash, a gap) is reported. */
    public static Result verify(List<AuditInvestigationLogEntry> inSeqOrder) {
        String prev = GENESIS;
        int expected = 1;
        for (AuditInvestigationLogEntry e : inSeqOrder) {
            if (e.getSeq() != expected || !prev.equals(e.getPrevHash())
                    || !hash(e.getSeq(), e.getAt(), e.getActorId(), e.getAction(), e.getDetail(), e.getPrevHash()).equals(e.getHash()))
                return new Result(false, inSeqOrder.size(), e.getSeq());
            prev = e.getHash();
            expected++;
        }
        return new Result(true, inSeqOrder.size(), null);
    }
}
