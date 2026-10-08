package za.co.handyflow.platform.shared;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Packs a user's permission names into one short string for the JWT. A user with the Clinic catalogue plus other modules
 * holds hundreds of names, and sent as a plain list they pushed the Authorization header past the 8 KB header limit of
 * Tomcat and of most proxies. Names are sorted (so shared prefixes sit together), deflated and base64url encoded, which
 * is several times smaller. Pure.
 */
public final class PermissionCodec {
    private PermissionCodec() {}

    /** Longest decoded list accepted, so a hostile token cannot expand into memory. */
    private static final int MAX_BYTES = 256 * 1024;

    public static String encode(Collection<String> permissions) {
        if (permissions == null || permissions.isEmpty()) return "";
        byte[] raw = String.join("\n", new TreeSet<>(permissions)).getBytes(StandardCharsets.UTF_8);
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION, true);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 3 + 16);
        byte[] buf = new byte[1024];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }

    public static Set<String> decode(String packed) {
        if (packed == null || packed.isEmpty()) return Set.of();
        Inflater inf = new Inflater(true);
        try {
            inf.setInput(Base64.getUrlDecoder().decode(packed));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) throw new IllegalArgumentException("Permission claim is too large");
            }
            if (!inf.finished()) throw new IllegalArgumentException("Permission claim is truncated");
            String text = out.toString(StandardCharsets.UTF_8);
            return text.isEmpty() ? Set.of() : Set.copyOf(new LinkedHashSet<>(Arrays.asList(text.split("\n"))));
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("Permission claim is not valid", e);
        } finally {
            inf.end();
        }
    }
}
