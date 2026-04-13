package enums;

import lombok.Getter;

@Getter
public enum WildcardStrategy {
    PERMUTERM("Permuterm index - handles all single-* patterns precisely"),
    BTREE("BTree/ReverseBTree - BTree for prefix, ReverseBTree for suffix, Permuterm for middle"),
    THREE_GRAM("3-gram index - supports multiple wildcards (*) via trigram intersection and regex post-filtering");

    private final String description;

    WildcardStrategy(String description) {
        this.description = description;
    }
}
