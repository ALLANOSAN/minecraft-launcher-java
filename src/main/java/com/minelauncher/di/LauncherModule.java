package com.minelauncher.di;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.minelauncher.launcher.GameLauncher;
import com.minelauncher.launcher.VersionManager;
import com.minelauncher.mods.ModManager;
import com.minelauncher.profiles.ProfileManager;
import com.minelauncher.settings.SettingsManager;
import com.minelauncher.ui.services.BackupService;
import com.minelauncher.ui.services.DiscordService;
import com.minelauncher.ui.services.UpdateService;

public class LauncherModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(SettingsManager.class).toInstance(SettingsManager.getInstance());
        
        // Services/Managers needed for injection
        bind(ProfileManager.class).toProvider(() -> new ProfileManager(SettingsManager.getInstance().getBaseDir()));
        bind(VersionManager.class).toProvider(() -> new VersionManager(SettingsManager.getInstance().getBaseDir()));
        bind(GameLauncher.class).toProvider(() -> new GameLauncher(SettingsManager.getInstance().getBaseDir(), new VersionManager(SettingsManager.getInstance().getBaseDir())));
        bind(ModManager.class).toProvider(() -> new ModManager(SettingsManager.getInstance().getBaseDir()));
        bind(BackupService.class).in(Singleton.class);
        bind(DiscordService.class).in(Singleton.class);
        bind(UpdateService.class).in(Singleton.class);
    }
}
