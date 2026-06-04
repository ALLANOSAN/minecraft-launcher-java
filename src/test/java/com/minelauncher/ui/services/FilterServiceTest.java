package com.minelauncher.ui.services;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FilterServiceTest {

    @Test
    void testFilter() {
        List<String> fullList = Arrays.asList("Alpha", "Beta", "Gamma", "Delta");
        
        // Test exact match (case insensitive)
        assertEquals(List.of("Alpha"), FilterService.filter("alpha", fullList));
        
        // Test partial match
        assertEquals(List.of("Beta", "Delta"), FilterService.filter("ta", fullList));
        
        // Test no match
        assertEquals(List.of(), FilterService.filter("Omega", fullList));
        
        // Test empty query (should return all)
        assertEquals(fullList, FilterService.filter("", fullList));
        assertEquals(fullList, FilterService.filter(null, fullList));
    }
}
