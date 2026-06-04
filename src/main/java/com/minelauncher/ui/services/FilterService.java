package com.minelauncher.ui.services;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service para operações de filtragem de listas na UI.
 */
public class FilterService {

    public static List<String> filter(String query, List<String> fullList) {
        if (query == null || query.isEmpty()) {
            return fullList;
        }
        String lowerQuery = query.toLowerCase();
        return fullList.stream()
                .filter(item -> item.toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
    }
}
