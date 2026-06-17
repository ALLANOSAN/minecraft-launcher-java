package com.minelauncher.di;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.minelauncher.launcher.GameLauncher;
import com.minelauncher.launcher.VersionManager;
import com.minelauncher.mods.ModManager;
import com.minelauncher.profiles.ProfileManager;
import com.minelauncher.settings.SettingsManager;
import com.minelauncher.ui.services.*;
import com.minelauncher.ui.controllers.ModActions;

public class LauncherModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(SettingsManager.class).toInstance(SettingsManager.getInstance());
        
        // Services/Managers needed for injection
        bind(VersionManager.class).toInstance(new VersionManager(SettingsManager.getInstance().getBaseDir()));
        bind(ProfileManager.class).toInstance(new ProfileManager(SettingsManager.getInstance().getBaseDir()));
        bind(ModManager.class).toInstance(new ModManager(SettingsManager.getInstance().getBaseDir()));
        bind(GameLauncher.class).toInstance(new GameLauncher(SettingsManager.getInstance().getBaseDir(), 
                new VersionManager(SettingsManager.getInstance().getBaseDir())));
        
        bind(BackupService.class).in(Singleton.class);
        bind(DiscordService.class).in(Singleton.class);
        bind(UpdateService.class).in(Singleton.class);
        bind(AuthService.class).in(Singleton.class);
        bind(VersionInstallationService.class).in(Singleton.class);
        bind(GameLaunchService.class).in(Singleton.class);
        bind(NavigationService.class).in(Singleton.class);
        bind(LauncherStateService.class).in(Singleton.class);
        bind(WindowService.class).in(Singleton.class);
        bind(ModActions.class).in(Singleton.class);
    }
}
