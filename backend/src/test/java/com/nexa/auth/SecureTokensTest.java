package com.nexa.auth;

import com.nexa.common.security.SecureTokens;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SecureTokensTest {

    @Test
    void generatesUrlSafeUniqueTokens() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = SecureTokens.generate();
            assertThat(token).matches("[A-Za-z0-9_-]{43}");
            tokens.add(token);
        }
        assertThat(tokens).hasSize(1000);
    }

    @Test
    void hashIsDeterministicSha256Hex() {
        assertThat(SecureTokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(SecureTokens.hash("abc")).isEqualTo(SecureTokens.hash("abc"));
    }
}
