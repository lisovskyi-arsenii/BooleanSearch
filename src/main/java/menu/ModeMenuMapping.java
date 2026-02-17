package menu;

import core.BooleanSearchEngine;
import enums.MenuChoice;

import java.util.List;

public final class ModeMenuMapping {

    private ModeMenuMapping() {
        throw new UnsupportedOperationException("ModeMenuMapping class cannot be instantiated");
    }

    public static List<MenuChoice> getChoicesForMode(BooleanSearchEngine.IndexingMode mode) {
        return switch (mode) {
            case NOT_INIT -> List.of(
                    // Initialization
                    MenuChoice.INDEX_DOCUMENTS,           // 1
                    MenuChoice.INDEX_LARGE_DOCUMENTS,     // 2
                    MenuChoice.LOAD_INDEX,                // 3
                    // File Management
                    MenuChoice.GENERATE_FILES,            // 4
                    MenuChoice.LIST_DIRECTORY,            // 5
                    MenuChoice.CLEAR_ALL_FILES            // 6
            );

            case IN_MEMORY -> List.of(
                    MenuChoice.REINDEX_DOCUMENTS,         // 1
                    MenuChoice.SIMPLE_SEARCH,             // 2
                    MenuChoice.AND_SEARCH,                // 3
                    MenuChoice.OR_SEARCH,                 // 4
                    MenuChoice.NOT_SEARCH,                // 5
                    MenuChoice.ADVANCED_SEARCH,           // 6
                    MenuChoice.PHRASE_SEARCH,             // 7
                    MenuChoice.PROXIMITY_SEARCH,          // 8
                    MenuChoice.WILDCARD_SEARCH,           // 9
                    MenuChoice.VIEW_STATISTICS,           // 10
                    MenuChoice.SHOW_TOP_TERMS,            // 11
                    MenuChoice.SAVE_INDEX,                // 12
                    MenuChoice.COMPARE_FORMATS,           // 13
                    MenuChoice.COMPARE_PERFORMANCE,       // 14
                    MenuChoice.COMPRESSION_PERFORMANCE,   // 15
                    MenuChoice.CLEAR_INDEX,               // 16
                    MenuChoice.GENERATE_FILES,            // 17
                    MenuChoice.LIST_DIRECTORY,            // 18
                    MenuChoice.CLEAR_ALL_FILES            // 19
            );


            case DISK_BASED -> List.of(
                    // Search
                    MenuChoice.SIMPLE_SEARCH,             // 1
                    MenuChoice.AND_SEARCH,                // 2
                    MenuChoice.OR_SEARCH,                 // 3
                    MenuChoice.NOT_SEARCH,                // 4
                    MenuChoice.ADVANCED_SEARCH,           // 5
                    MenuChoice.PHRASE_SEARCH,             // 6
                    MenuChoice.WILDCARD_SEARCH,           // 7
                    // Analytics
                    MenuChoice.VIEW_STATISTICS,           // 8
                    // File Management
                    MenuChoice.GENERATE_FILES,            // 9
                    MenuChoice.LIST_DIRECTORY,            // 10
                    MenuChoice.CLEAR_ALL_FILES,           // 11
                    // Utility
                    MenuChoice.CLEAR_INDEX                // 12
            );
        };
    }

    public static MenuChoice resolveChoice(int userInput, BooleanSearchEngine.IndexingMode mode) {
        if (userInput == 0) {
            return MenuChoice.EXIT;
        }

        List<MenuChoice> choices = getChoicesForMode(mode);

        int index = userInput - 1;

        if (index < 0 || index >= choices.size()) {
            return null;
        }

        return choices.get(index);
    }
}
