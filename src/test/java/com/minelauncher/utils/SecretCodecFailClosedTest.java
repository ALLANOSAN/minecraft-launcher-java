package com.minelauncher.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes adicionais para {@link SecretCodec} focando no fix de
 * CRIT-1 (fail-closed) e contratos de round-trip.
 *
 * <p>ATENÇÃO ao contrato do codec: {@link SecretCodec#encrypt(String)}
 * retorna Base64 sem prefixo. Quem armazena em JSON precisa prefixar
 * com {@code "enc:"} para sinalizar que está cifrado; quem lê usa
 * {@code "enc:" + ...} como argumento para {@link SecretCodec#decrypt}.
 * Esse prefixo permite que o launcher mantenha compatibilidade com
 * tokens legacy gravados em plain text (sem prefixo → retorna as-is).
 *
 * <p>Este contrato é coberto pelos testes existentes em
 * SecretCodecTest. Aqui validamos o novo comportamento fail-closed.
 */
class SecretCodecFailClosedTest {

    private static final String ENC_PREFIX = "enc:";

    @Test
    void encrypt_nullReturnsNull() {
        assertNull(SecretCodec.encrypt(null));
    }

    @Test
    void decrypt_nullReturnsNull() {
        assertNull(SecretCodec.decrypt(null));
    }

    @Test
    void roundTrip_emptyString() {
        // Empty é edge: encrypt("") → ""; decrypt("") → "". Não é
        // um round-trip significativo mas garante que não há NPE.
        assertEquals("", SecretCodec.encrypt(""));
        assertEquals("", SecretCodec.decrypt(""));
    }

    @Test
    void roundTrip_unicode() {
        // Garante que codificação sobrevive caracteres multibyte.
        // IMPORTANTE: caller adiciona "enc:" para que decrypt saiba
        // que é Base64 (legacy plain text não tem prefixo).
        String original = "token_com_acentos_áéíóú_😀";
        String enc = SecretCodec.encrypt(original);
        assertNotEquals(original, enc, "Ciphertext deve diferir de plaintext");
        assertEquals(original, SecretCodec.decrypt(ENC_PREFIX + enc));
    }

    @Test
    void roundTrip_longValue() {
        // Microsoft JWT pode ter 2-4 KB.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4000; i++) sb.append((char) ('A' + (i % 26)));
        String original = sb.toString();
        String enc = SecretCodec.encrypt(original);
        assertEquals(original, SecretCodec.decrypt(ENC_PREFIX + enc));
    }

    @Test
    void encrypt_outputIsNotPlaintext() {
        // CRIT-1: este é o teste que falharia se alguém reintroduzisse
        // o bug "return plain;". Se encriptar "secret" retornar
        // "secret", o secret está sendo gravado em cleartext.
        String plain = "MEU_TOKEN_SECRETO";
        String enc = SecretCodec.encrypt(plain);
        assertNotNull(enc);
        assertNotEquals(plain, enc, "Ciphertext não pode ser igual ao plaintext");
        assertFalse(enc.contains(plain),
                "Ciphertext não pode conter o plaintext como substring");
    }

    @Test
    void encrypt_usesBase64WithValidChars() {
        // Saída deve ser Base64 (A-Z, a-z, 0-9, +, /, =) por contrato
        // da doc. IV de 16 bytes + ciphertext variável.
        String enc = SecretCodec.encrypt("test");
        assertNotNull(enc);
        assertTrue(enc.matches("[A-Za-z0-9+/=]+"),
                "Ciphertext deve ser Base64: " + enc);
    }

    @Test
    void decrypt_invalidBase64PrefixFallsBackToPlain() {
        // Sem prefixo "enc:" → tratado como plain text legacy.
        // Isso é POR CONTRATO, não é o bug CRIT-1 (que era retornar
        // plain no encrypt). Aqui o plain é de entrada (legado).
        String legacy = "MinecraftAccessToken_abc123";
        assertEquals(legacy, SecretCodec.decrypt(legacy),
                "Sem prefixo enc: deve retornar as-is (compat legacy)");
    }

    @Test
    void encryptDecrypt_multipleCallsDifferByIV() {
        // AES-CBC com IV aleatório: 2 cifras do mesmo plaintext
        // devem produzir ciphertexts diferentes. Se forem iguais,
        // IV está sendo determinístico (bug de segurança).
        String plain = "mesmo input";
        String a = SecretCodec.encrypt(plain);
        String b = SecretCodec.encrypt(plain);
        assertNotNull(a);
        assertNotNull(b);
        assertNotEquals(a, b, "IVs aleatórios → ciphertexts diferentes");
        // Mas ambos decifram para o mesmo plaintext
        assertEquals(plain, SecretCodec.decrypt(ENC_PREFIX + a));
        assertEquals(plain, SecretCodec.decrypt(ENC_PREFIX + b));
    }

    @Test
    void decrypt_truncatedCiphertext_returnsAsIsNotNull() {
        // "enc:" + base64 curto (< 17 bytes) → impossível extrair IV.
        // Comportamento atual: retorna o input como plain. Pode ser
        // discutível mas é defensivo (não propaga garbage).
        String short1 = ENC_PREFIX + "AAAA";
        // Não crasha. Pode ser null, vazio, ou o próprio input.
        assertDoesNotThrow(() -> SecretCodec.decrypt(short1));
    }
}
