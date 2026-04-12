package index;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@Getter
public class PermutermIndex implements WildcardIndex {
    private static final char MARKER = '$';

    // red-black tree map: rotation -> original term
    private final TreeMap<String, String> rotationMap = new TreeMap<>();
    private final Set<String> terms = new HashSet<>();

    @Override
    public void buildFromDictionary(Dictionary dictionary) {
        rotationMap.clear();
        terms.clear();

        for (String term : dictionary.getAllTerms()) {
            addTerm(term);
        }

        log.info("Built permuterm index: {} unique terms, {} rotations",
                terms.size(), rotationMap.size());
    }

    @Override
    public void addTerm(String term) {
        if (term == null || term.isBlank()) {
            throw new IllegalArgumentException("Term is null or blank");
        }
        if (term.indexOf(MARKER) >= 0) {
            throw new IllegalArgumentException(
                    "Term cannot contain marker '" + MARKER + "': " + term
            );
        }
        if (!terms.add(term)) {
            return;
        }

        // add marker and then rotate word
        String extended = term + MARKER;

        for (int i = 0; i < extended.length(); i++) {
            String rotated = extended.substring(i) + extended.substring(0, i);
            rotationMap.put(rotated, term);
        }
    }

    @Override
    public List<String> search(String wildcardQuery) {
        if (wildcardQuery == null || wildcardQuery.isBlank()) {
            return new ArrayList<>();
        }

        if (!wildcardQuery.contains("*")) {
            return contains(wildcardQuery)
                    ? List.of(wildcardQuery)
                    : Collections.emptyList();
        }

        if (wildcardQuery.equals("*")) {
            List<String> all = new ArrayList<>(terms);
            Collections.sort(all);
            return all;
        }

        long starCount = wildcardQuery.chars().filter(c -> c == '*').count();
        if (starCount > 1) {
            log.warn("PermutermIndex supports only one '*', got query: {}. " +
                    "Use ThreeGramIndex for multi-wildcard queries.", wildcardQuery);
            return Collections.emptyList();
        }

        String prefix = buildSearchPrefix(wildcardQuery);
        return findByPrefix(prefix);
    }

    @Override
    public boolean contains(String term) {
        return terms.contains(term);
    }

    @Override
    public int size() {
        return terms.size();
    }

    @Override
    public void clear() {
        rotationMap.clear();
        terms.clear();
    }

    private String buildSearchPrefix(String wildcardQuery) {
        int starIdx = wildcardQuery.indexOf('*');
        String before = wildcardQuery.substring(0, starIdx);
        String after  = wildcardQuery.substring(starIdx + 1);
        return after + MARKER + before;
    }

    private List<String> findByPrefix(String prefix) {
        String upperBound = nextPrefix(prefix);

        Collection<String> subMapValues = (upperBound != null)
                ? rotationMap.subMap(prefix, upperBound).values()
                : rotationMap.tailMap(prefix).values();

        Set<String> unique = new LinkedHashSet<>(subMapValues);

        List<String> result = new ArrayList<>(unique);
        Collections.sort(result);
        return result;
    }

    private String nextPrefix(String prefix) {
        if (prefix.isEmpty()) return null;
        char last = prefix.charAt(prefix.length() - 1);
        if (last == Character.MAX_VALUE) return null;
        return prefix.substring(0, prefix.length() - 1) + (char) (last + 1);
    }
}
