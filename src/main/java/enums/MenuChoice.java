package enums;

import lombok.Getter;

public enum MenuChoice {
    EXIT("Exit"),
    INDEX_DOCUMENTS("Index documents"),
    REINDEX_DOCUMENTS("Re-index"),
    INDEX_LARGE_DOCUMENTS("Index large collection of documents"),
    GENERATE_FILES("Generate files"),
    LIST_DIRECTORY("List directory"),
    CLEAR_ALL_FILES("Clear all files"),
    SIMPLE_SEARCH("Simple search"),
    AND_SEARCH("AND search"),
    OR_SEARCH("OR search"),
    NOT_SEARCH("NOT search"),
    ADVANCED_SEARCH("Advanced search (query parser)"),
    PHRASE_SEARCH("Phrase search"),
    PROXIMITY_SEARCH("Proximity search"),
    WILDCARD_SEARCH("Wildcard search"),
    ZONE_RANKING_SEARCH("Zone ranking search"),
    CLUSTER_DOCUMENTS("Cluster documents (K-Means)"),
    VIEW_STATISTICS("View statistics"),
    SHOW_TOP_TERMS("Show top N terms"),
    SAVE_INDEX("Save index"),
    LOAD_INDEX("Load index"),
    COMPARE_FORMATS("Compare formats"),
    CLEAR_INDEX("Clear index"),
    COMPARE_PERFORMANCE("Compare performance"),
    COMPRESSION_PERFORMANCE("Compression performance");

    @Getter
    private final String description;

    MenuChoice(String description) {
        this.description = description;
    }
}