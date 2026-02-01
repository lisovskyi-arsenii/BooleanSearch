import core.BooleanSearchEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import query.ProximitySearch;

import java.io.IOException;

import static constants.Filenames.DIRECTORY_PATH;

class ProximitySearchTest {

    private ProximitySearch search;

    @BeforeEach
    void setUp() throws IOException {
        var engine = new BooleanSearchEngine();
        engine.indexDocuments(DIRECTORY_PATH);
        search = new ProximitySearch(engine.getPositionalIndex(), engine.getBiwordIndex(), engine);
    }

    @Test
    void testSearchProximity() {
        final String term1 = "common";
        final String term2 = "with";

        var result = search.searchProximity(term1, term2, 4);
        System.out.println(result);
    }

}
