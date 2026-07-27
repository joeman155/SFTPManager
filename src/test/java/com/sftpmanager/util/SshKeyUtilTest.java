package com.sftpmanager.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SshKeyUtilTest {

    @Test
    void nullInputReturnsNull() {
        assertThat(SshKeyUtil.toRfc4716(null)).isNull();
    }

    @Test
    void blankInputReturnsNull() {
        assertThat(SshKeyUtil.toRfc4716("   ")).isNull();
    }

    @Test
    void convertsSimpleKeyWithoutComment() {
        String result = SshKeyUtil.toRfc4716("ssh-rsa AAAAB3NzaC1yc2E");

        assertThat(result)
            .startsWith("---- BEGIN SSH2 PUBLIC KEY ----\n")
            .endsWith("---- END SSH2 PUBLIC KEY ----")
            .contains("AAAAB3NzaC1yc2E")
            .doesNotContain("Comment:");
    }

    @Test
    void includesCommentWhenPresent() {
        String result = SshKeyUtil.toRfc4716("ssh-rsa AAAAB3NzaC1yc2E user@host");

        assertThat(result).contains("Comment: \"user@host\"");
    }

    @Test
    void stripsDoubleQuotesFromComment() {
        String result = SshKeyUtil.toRfc4716("ssh-rsa AAAAB3NzaC1yc2E my \"quoted\" comment");

        assertThat(result).contains("Comment: \"my quoted comment\"");
    }

    @Test
    void wrapsBase64PayloadAt70Characters() {
        String b64 = "A".repeat(150); // forces 3 wrapped lines: 70 + 70 + 10
        String result = SshKeyUtil.toRfc4716("ssh-rsa " + b64);

        String[] lines = result.split("\n");
        // BEGIN, 70, 70, 10, END
        assertThat(lines).hasSize(5);
        assertThat(lines[1]).hasSize(70);
        assertThat(lines[2]).hasSize(70);
        assertThat(lines[3]).hasSize(10);
    }

    @Test
    void treatsBareBlobWithoutTypeAsPayload() {
        // Single token — no "ssh-rsa" prefix; the blob itself is the payload
        String result = SshKeyUtil.toRfc4716("AAAAB3NzaC1yc2E");

        assertThat(result).contains("AAAAB3NzaC1yc2E");
    }

    @Test
    void handlesMultipleSpacesBetweenParts() {
        String result = SshKeyUtil.toRfc4716("ssh-ed25519    AAAAC3Nza    my-laptop");

        assertThat(result)
            .contains("AAAAC3Nza")
            .contains("Comment: \"my-laptop\"");
    }
}
