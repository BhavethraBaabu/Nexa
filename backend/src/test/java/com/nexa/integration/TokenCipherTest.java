package com.nexa.integration;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final TokenCipher cipher = new TokenCipher(properties(KEY));

    @Test
    void roundTripsAndNeverStoresPlaintext() {
        String encrypted = cipher.encrypt("xoxb-secret-token");
        assertThat(encrypted).doesNotContain("xoxb").doesNotContain("secret");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("xoxb-secret-token");
    }

    @Test
    void sameValueEncryptsDifferentlyEachTime() {
        assertThat(cipher.encrypt("token")).isNotEqualTo(cipher.encrypt("token"));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        byte[] bytes = Base64.getDecoder().decode(cipher.encrypt("token"));
        bytes[bytes.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(bytes))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrongKeyCannotDecrypt() {
        String other = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
        assertThatThrownBy(() -> new TokenCipher(properties(other)).decrypt(cipher.encrypt("token"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void keyMustBe256Bits() {
        assertThatThrownBy(() -> new TokenCipher(properties(Base64.getEncoder().encodeToString(new byte[16]))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
    }

    @Test
    void nullsPassThrough() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    private static IntegrationProperties properties(String key) {
        return new IntegrationProperties(key, "http://localhost:8080", "http://localhost:3000",
                new IntegrationProperties.Jira(null, null, "https://auth.atlassian.com", "https://api.atlassian.com"),
                new IntegrationProperties.Slack(null, null, "https://slack.com"));
    }
}
