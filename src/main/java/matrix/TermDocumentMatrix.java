package matrix;

import core.Dictionary;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class TermDocumentMatrix implements Dictionary {
    private static final int INITIAL_SIZE = 1000;
    private final Map<String, Integer> termToIndex;
    private final Map<Integer, Integer> docToIndex;
    private final Map<Integer, Integer> indexToDoc;
    private boolean[][] matrix; // [термін][документ]
    private int termCount = 0;
    private int docCount = 0;
    private int totalOccurrences = 0;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();


    public TermDocumentMatrix() {
        termToIndex = new ConcurrentHashMap<>();
        docToIndex = new ConcurrentHashMap<>();
        indexToDoc = new ConcurrentHashMap<>();
        matrix = new boolean[INITIAL_SIZE][INITIAL_SIZE];
    }

    public void addTerm(String term, int docId) {
        lock.writeLock().lock();
        try {
            int termIdx = termToIndex.computeIfAbsent(term, _ -> termCount++);
            int docIdx = docToIndex.computeIfAbsent(docId, _ -> {
                indexToDoc.put(docCount, docId);
                return docCount++;
            });
            ensureCapacity(termIdx, docIdx);

            if (!matrix[termIdx][docIdx]) {
                matrix[termIdx][docIdx] = true;
                totalOccurrences++;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<Set<Integer>> getDocuments(String term) {
        lock.readLock().lock();
        try {
            Integer termIdx = termToIndex.get(term);
            if (termIdx == null) return Optional.empty();

            Set<Integer> documents = new TreeSet<>();
            for (int docIdx = 0; docIdx < docCount; docIdx++) {
                if (matrix[termIdx][docIdx]) {
                    documents.add(indexToDoc.get(docIdx));
                }
            }

            return Optional.of(documents);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int size() {
        return termCount;
    }

    @Override
    public int getTotalTermOccurrences() {
        lock.readLock().lock();
        try {
            return totalOccurrences;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            termToIndex.clear();
            docToIndex.clear();
            indexToDoc.clear();
            matrix = new boolean[INITIAL_SIZE][INITIAL_SIZE];
            termCount = 0;
            docCount = 0;
            totalOccurrences = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void ensureCapacity(int termIdx, int docIdx) {
        int newTermSize = matrix.length;
        int newDocSize = matrix[0].length;

        while (termIdx >= newTermSize) newTermSize *= 2;
        while (docIdx >= newDocSize) newDocSize *= 2;

        if (newTermSize != matrix.length || newDocSize != matrix[0].length) {
            boolean[][] newMatrix = new boolean[newTermSize][newDocSize];
            for (int i = 0; i < matrix.length; i++) {
                System.arraycopy(matrix[i], 0, newMatrix[i], 0, matrix[i].length);
            }
            matrix = newMatrix;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (boolean[] terms : matrix) {
            for (boolean docs : terms) {
                sb.append(docs).append(" ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
