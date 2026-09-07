package io.github.monkeyinspector.source;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record SourceRevision(String hash) {
    public static SourceRevision of(String text) {
        try {
            return new SourceRevision(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
