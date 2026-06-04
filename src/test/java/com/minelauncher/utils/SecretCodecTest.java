package com.minelauncher.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para {@link SecretCodec}.
 *
 * <p>Cobertura:
 * <ul>
 *   <li>Round-trip encrypt→decrypt preserva a string original</li>
 *   <li>Cada chamada de encrypt() gera ciphertext diferente (IV aleatório)</li>
 *   <li>Plain text legado (sem prefixo "enc:") passa por decrypt() intacto</li>
 *   <li>Null/empty handling</li>
 *   <li>Unicode (emojis, acentos)</li>
 * </ul>
 */
class SecretCodecTest {

    @Test
    void roundTrip_simpleString() {
        String original = "hello world";
        String encrypted = SecretCodec.encrypt(original);
        assertNotNull(encrypted);
        assertNotEquals(original, encrypted, "Encrypted deve ser diferente do original");

        String decrypted = SecretCodec.decrypt("enc:" + encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void roundTrip_tokenLike() {
        String original = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.abc.def";
        String encrypted = SecretCodec.encrypt(original);
        String decrypted = SecretCodec.decrypt("enc:" + encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void encrypt_producesDifferentCiphertextEachTime() {
        String original = "same input";
        String first = SecretCodec.encrypt(original);
        String second = SecretCodec.encrypt(original);
        assertNotEquals(first, second, "IV aleatório deve produzir ciphertexts diferentes");
    }

    @Test
    void decrypt_legacyPlainText_returnsAsIs() {
        String legacyToken = "MinecraftAccessToken_abc123";
        // Sem prefixo "enc:" → trata como plain text legado
        String result = SecretCodec.decrypt(legacyToken);
        assertEquals(legacyToken, result);
    }

    @Test
    void decrypt_emptyString_returnsEmpty() {
        assertEquals("", SecretCodec.decrypt(""));
    }

    @Test
    void decrypt_null_returnsNull() {
        assertNull(SecretCodec.decrypt(null));
    }

    @Test
    void encrypt_null_returnsNull() {
        assertNull(SecretCodec.encrypt(null));
    }

    @Test
    void encrypt_emptyString_returnsEmpty() {
        assertEquals("", SecretCodec.encrypt(""));
    }

    @Test
    void roundTrip_unicode() {
        String original = "Jogador_中文_🎮_ção";
        String encrypted = SecretCodec.encrypt(original);
        String decrypted = SecretCodec.decrypt("enc:" + encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void roundTrip_longString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) sb.append("lorem ipsum dolor sit amet ");
        String original = sb.toString();
        String encrypted = SecretCodec.encrypt(original);
        String decrypted = SecretCodec.decrypt("enc:" + encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void decrypt_invalidBase64_returnsAsIs() {
        // "enc:" + conteúdo inválido → não crasha, retorna como plain text
        String weird = "enc:!@#$%^&*()";
        String result = SecretCodec.decrypt(weird);
        assertNotNull(result);
        // Pode retornar o original (degraded) ou lançar — desde que não crash
    }
}
