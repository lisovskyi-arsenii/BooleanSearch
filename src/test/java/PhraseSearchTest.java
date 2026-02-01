import core.BooleanSearchEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import query.PhraseSearch;

import java.io.IOException;

import static constants.Filenames.DIRECTORY_PATH;

class PhraseSearchTest {

    private PhraseSearch search;

    @BeforeEach
    void setUp() throws IOException {
        var engine = new BooleanSearchEngine();
        engine.indexDocuments(DIRECTORY_PATH);
        search = new PhraseSearch(engine.getPositionalIndex(), engine.getBiwordIndex(), engine);
    }

    @Test
    void testSearch() {
        var result = search.searchPhraseBiword("common with");
        assert(result.isPresent());
    }
}
