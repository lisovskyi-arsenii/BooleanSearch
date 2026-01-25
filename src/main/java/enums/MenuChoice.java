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
    VIEW_STATISTICS(11, "View statistics"),
    SHOW_TOP_TERMS(12, "Show top N terms"),
    SAVE_INDEX(13, "Save index"),
    LOAD_INDEX(14, "Load index"),
    COMPARE_FORMATS(15, "Compare formats"),
    CLEAR_INDEX(16, "Clear index"),
    PRINT_INDEX(17, "Print index"),
    COMPARE_PERFORMANCE(18, "Compare performance");

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