package com.minelauncher.ui.services;

import com.minelauncher.launcher.GameLauncher;
import com.minelauncher.launcher.VersionManager;
import com.minelauncher.models.LaunchProfile;
import org.junit.jupiter.api.Test;
import java.io.File;
import static org.mockito.Mockito.*;

class GameLaunchServiceTest {

    @Test
    void testLaunchOrchestration() throws Exception {
        VersionManager versionManager = mock(VersionManager.class);
        GameLauncher gameLauncher = mock(GameLauncher.class);
        BackupService backupService = mock(BackupService.class);
        GameLaunchService service = new GameLaunchService(versionManager, gameLauncher, backupService);
        
        LaunchProfile profile = new LaunchProfile("test", "1.21");
        
        // Simular que a versão não está instalada
        when(versionManager.getInstalledVersions()).thenReturn(java.util.Collections.emptyList());
        
        // Trigger launch (we don't wait for actual thread completion as we just want to verify orchestration)
        service.launch(profile, null, s -> {}, (s, p) -> {}, s -> {}, () -> {}, () -> {}, e -> {});
        
        // Verify download was triggered
        verify(versionManager, timeout(1000)).downloadVersion(eq("1.21"), any());
    }
}
