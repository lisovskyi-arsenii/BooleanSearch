package core;

import enums.SearchStructureType;
import index.BiwordIndex;
import index.PositionalIndex;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import query.PhraseSearch;
import query.ProximitySearch;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class SearchService {
    @Getter
    private final BooleanSearchEngine engine;
    private final PhraseSearch phraseSearch;
    private final ProximitySearch proximitySearch;

    public SearchService(BooleanSearchEngine engine) {
        this.engine = engine;

        BiwordIndex biwordIndex = engine.getBiwordIndex();
        PositionalIndex positionalIndex = engine.getPositionalIndex();

        this.phraseSearch = new PhraseSearch(positionalIndex, biwordIndex);
        this.proximitySearch = new ProximitySearch(positionalIndex, biwordIndex);
    }


    // Boolean Search
    public Optional<Set<Integer>> search(String term, SearchStructureType type) {
        return engine.search(term, type);
    }

    public Optional<Set<Integer>> andSearch(String t1, String t2, SearchStructureType type) {
        return engine.andSearch(t1, t2, type);
    }

    public Optional<Set<Integer>> orSearch(String t1, String t2, SearchStructureType type) {
        return engine.orSearch(t1, t2, type);
    }

    public Optional<Set<Integer>> notSearch(String term, Set<Integer> docIds, SearchStructureType type) {
        return engine.notSearch(term, docIds, type);
    }

    // Phrase Search
    public Optional<Set<Integer>> phraseSearch(String phrase, SearchStructureType type) {
        return phraseSearch.search(phrase, type);
    }

    // Proximity Search
    public Optional<Set<ProximitySearch.ProximityMatch>> proximitySearch(String term1, String term2, int k) {
        return proximitySearch.searchProximity(term1, term2, k);
    }

    // Utility
    public List<String> getDocumentNames(Set<Integer> docIds) {
        return engine.getDocumentNames(docIds);
    }
}
