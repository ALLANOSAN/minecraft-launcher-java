package com.minelauncher.ui.services;

import com.minelauncher.models.LaunchProfile;
import org.junit.jupiter.api.Test;
import java.io.File;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemUIServiceTest {

    @Test
    void testResolveGameDir() {
        File baseDir = new File("/home/user/.minecraft");
        
        // Test null/empty gameDir (returns baseDir)
        LaunchProfile p1 = new LaunchProfile("p1", "1.21");
        assertEquals(baseDir, FileSystemUIService.resolveGameDir(p1, baseDir));
        
        // Test absolute gameDir
        LaunchProfile p2 = new LaunchProfile("p2", "1.21");
        p2.setGameDir("/custom/dir");
        File expectedAbsolute = new File("/custom/dir");
        assertEquals(expectedAbsolute, FileSystemUIService.resolveGameDir(p2, baseDir));
        
        // Test relative gameDir
        LaunchProfile p3 = new LaunchProfile("p3", "1.21");
        p3.setGameDir("custom/subdir");
        File expectedRelative = new File(baseDir, "custom/subdir");
        assertEquals(expectedRelative, FileSystemUIService.resolveGameDir(p3, baseDir));
    }
}
