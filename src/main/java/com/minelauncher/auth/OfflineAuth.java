package com.minelauncher.auth;

import com.minelauncher.models.GameProfile;
import java.util.UUID;

public class OfflineAuth {

    /**
     * Cria um perfil offline com UUID determinístico baseado no nome
     */
    public static GameProfile createOfflineProfile(String playerName) {
        // UUID determinístico baseado no nome (padrão do Minecraft offline)
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes());

        GameProfile profile = new GameProfile(playerName, uuid, false);
        profile.setOffline(true);
        profile.setAccessToken("0"); // Token dummy para modo offline
        profile.setTokenExpiry(Long.MAX_VALUE);

        return profile;
    }
}
