package index;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Pattern;

@Slf4j
@Getter
public class ThreeGramIndex implements WildcardIndex {
    private static final char MARKER = '$';
    private static final int N = 3;

    private final Map<String, Set<String>> index = new ConcurrentSkipListMap<>();
    private final Set<String> terms = ConcurrentHashMap.newKeySet();

    @Override
    public void buildFromDictionary(Dictionary dictionary) throws IllegalArgumentException {
        index.clear();
        terms.clear();

        for (String term : dictionary.getAllTerms()) {
            addTerm(term);
        }

        log.info("Built {}-gram index: {} unique terms, {} n-grams",
                N, terms.size(), index.size());
    }

    @Override
    public void addTerm(String term) throws IllegalArgumentException {
        if (term == null || term.isBlank()) {
            throw new IllegalArgumentException("Term is null or blank");
        }

        terms.add(term);

        String editedTerm = "$" + term + "$";

        for (int i = 0; i <= editedTerm.length() - N; i++) {
            String nGram = editedTerm.substring(i, i + N);
            index.computeIfAbsent(nGram, key -> new HashSet<>()).add(term);
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
            return new ArrayList<>(terms);
        }

        List<String> nGrams = extractNGrams(wildcardQuery);

        if (nGrams.isEmpty()) {
            return filterByRegex(new ArrayList<>(terms), wildcardQuery);
        }

        Set<String> candidates = findCandidates(nGrams);

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        return filterByRegex(new ArrayList<>(candidates), wildcardQuery);
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
        index.clear();
        terms.clear();
    }

    public List<String> extractNGrams(String wildcardQuery) {
        List<String> nGrams = new ArrayList<>();
        String edited = MARKER + wildcardQuery + MARKER;

        for (int i = 0; i <= edited.length() - N; i++) {
            String subStr = edited.substring(i, i + N);
            if (!subStr.contains("*")) {
                nGrams.add(subStr);
            }
        }

        return nGrams;
    }

    private Set<String> findCandidates(List<String> nGrams) {
        Set<String> candidates = null;

        for (String nGram : nGrams) {
            Set<String> termsForNGram = index.get(nGram);

            if (termsForNGram == null || termsForNGram.isEmpty()) {
                return Collections.emptySet();
            }

            if (candidates == null) {
                candidates = new HashSet<>(termsForNGram);
            } else {
                candidates.retainAll(termsForNGram);
            }

            if (candidates.isEmpty()) {
                return Collections.emptySet();
            }
        }

        return candidates != null ? candidates : Collections.emptySet();
    }

    private List<String> filterByRegex(List<String> termsList, String wildcardQuery) {
        String regex = wildcardToRegex(wildcardQuery);
        Pattern pattern = Pattern.compile(regex);

        return termsList.stream()
                .filter(term -> pattern.matcher(term).matches())
                .sorted()
                .toList();
    }

    private String wildcardToRegex(String wildcardQuery) {
        StringBuilder regex = new StringBuilder();

        for (char c : wildcardQuery.toCharArray()) {
            if (c == '*') {
                regex.append(".*");
            } else if (isRegexSpecialChar(c)) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }

        return regex.toString();
    }

    private boolean isRegexSpecialChar(char c) {
        return "[]{}()+.^$|?\\".indexOf(c) >= 0;
    }
}
