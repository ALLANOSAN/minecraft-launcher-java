package com.minelauncher.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformUtilTest {

    @Test
    void getOSKey_returns_known_value() {
        String osKey = PlatformUtil.getOSKey();
        // Em qualquer JVM de teste rodando, é um dos 3 valores
        assertTrue(osKey.equals("windows") || osKey.equals("osx") || osKey.equals("linux"),
                "OS key inesperada: " + osKey);
    }

    @Test
    void isWindows_matches_getOSKey() {
        assertEquals(PlatformUtil.isWindows(), PlatformUtil.getOSKey().equals("windows"));
    }

    @Test
    void isMac_matches_getOSKey() {
        assertEquals(PlatformUtil.isMac(), PlatformUtil.getOSKey().equals("osx"));
    }

    @Test
    void isLinux_matches_getOSKey() {
        assertEquals(PlatformUtil.isLinux(), PlatformUtil.getOSKey().equals("linux"));
    }

    @Test
    void exactly_one_of_isWindows_isMac_isLinux_is_true() {
        // Em qualquer SO válido, exatamente um dos 3 é true
        int count = (PlatformUtil.isWindows() ? 1 : 0)
                  + (PlatformUtil.isMac() ? 1 : 0)
                  + (PlatformUtil.isLinux() ? 1 : 0);
        assertEquals(1, count, "Exatamente um dos isWindows/isMac/isLinux deve ser true");
    }
}
