package query;

import com.google.common.collect.Sets;
import core.BooleanSearchEngine;
import enums.SearchStructureType;
import index.BiwordIndex;
import index.PositionalIndex;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class PhraseSearch {
    private final BooleanSearchEngine searchEngine;
    private final PositionalIndex positionalIndex;
    private final BiwordIndex biwordIndex;

    public PhraseSearch(PositionalIndex positionalIndex, BiwordIndex biwordIndex, BooleanSearchEngine searchEngine) {
        this.searchEngine = searchEngine;
        this.positionalIndex = positionalIndex;
        this.biwordIndex = biwordIndex;
    }

    public Optional<Set<Integer>> searchPhraseBiword(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return Optional.empty();
        }

        String[] tokens = phrase.split("\\s+");

        if (tokens.length < 2) {
            log.debug("Phrase '{}' has less than 2 tokens, cannot form biwords", phrase);
            return Optional.empty();
        }

        System.out.println(Arrays.toString(tokens));

        Set<Integer> result = null;

        for (int i = 0; i < tokens.length - 1; i++) {
            if (tokens[i].isBlank() || tokens[i + 1].isBlank()) {
                continue;
            }

            final String biword = tokens[i] + " "  + tokens[i + 1];

            var temp = biwordIndex.getBiword(tokens[i], tokens[i + 1]);
            System.out.println("Temp: " + temp);
            var tempResult = searchEngine.search(biword, SearchStructureType.BIWORD);
            if (tempResult.isEmpty()) {
                log.debug("Phrase " + phrase + " not found");
                return Optional.empty();
            }

            if (result == null) {
                result = new HashSet<>(tempResult.get());
            } else {
                result = new HashSet<>(Sets.intersection(result, tempResult.get()));
                if (result.isEmpty()) {
                    log.debug("Phrase " + phrase + " not found");
                    return Optional.empty();
                }
            }
        }

        System.out.println(Arrays.toString(tokens));

        return Optional.ofNullable(result);
    }

    public Optional<Set<Integer>> searchPhrasePositional(List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return Optional.empty();
        }

        Set<Integer> result = new HashSet<>();


        return Optional.empty();
    }
}
