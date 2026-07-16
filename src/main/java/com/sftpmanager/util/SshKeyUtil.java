package com.sftpmanager.util;

public final class SshKeyUtil {

    private SshKeyUtil() {}

    /**
     * Converts an OpenSSH public key line ("ssh-rsa AAAA... comment") to
     * RFC 4716 format, which older ProFTPD mod_sftp versions require for
     * SFTPAuthorizedUserKeys. Returns null for blank input.
     */
    public static String toRfc4716(String opensshKey) {
        if (opensshKey == null || opensshKey.isBlank()) return null;
        String[] parts = opensshKey.trim().split("\\s+", 3);
        // "type base64 [comment]" — the base64 blob is the payload
        String b64 = parts.length >= 2 ? parts[1] : parts[0];
        String comment = parts.length >= 3 ? parts[2] : null;

        StringBuilder sb = new StringBuilder("---- BEGIN SSH2 PUBLIC KEY ----\n");
        if (comment != null && !comment.isBlank()) {
            sb.append("Comment: \"").append(comment.replace("\"", "")).append("\"\n");
        }
        for (int i = 0; i < b64.length(); i += 70) {
            sb.append(b64, i, Math.min(i + 70, b64.length())).append('\n');
        }
        sb.append("---- END SSH2 PUBLIC KEY ----");
        return sb.toString();
    }
}
