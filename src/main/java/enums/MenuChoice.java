package enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

public enum MenuChoice {
    EXIT(0, "Exit"),
    INDEX_DOCUMENTS(1, "Index documents"),
    REINDEX_DOCUMENTS(2, "Re-index"),
    GENERATE_FILES(3, "Generate files"),
    LIST_DIRECTORY(4, "List directory"),
    CLEAR_ALL_FILES(5, "Clear all files"),
    SIMPLE_SEARCH(6, "Simple search"),
    AND_SEARCH(7, "AND search"),
    OR_SEARCH(8, "OR search"),
    NOT_SEARCH(9, "NOT search"),
    ADVANCED_SEARCH(10, "Advanced search (query parser)"),
    PHRASE_SEARCH(11, "Phrase search"),
    PROXIMITY_SEARCH(12, "Proximity search"),
    VIEW_STATISTICS(13, "View statistics"),
    SHOW_TOP_TERMS(14, "Show top N terms"),
    SAVE_INDEX(15, "Save index"),
    LOAD_INDEX(16, "Load index"),
    COMPARE_FORMATS(17, "Compare formats"),
    CLEAR_INDEX(18, "Clear index"),
    COMPARE_PERFORMANCE(19, "Compare performance");

    private final int code;
    @Getter
    private final String description;

    MenuChoice(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static Optional<MenuChoice> fromCode(int code) {
        return Arrays.stream(values())
                .filter(choice -> choice.code == code)
                .findFirst();
    }
}