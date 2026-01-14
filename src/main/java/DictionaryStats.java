public class DictionaryStats {
    private int documentsCount;
    private int uniqueTerms;
    private int totalWords;
    private long dictionarySizeInBytes;

    public DictionaryStats(int documentsCount, int uniqueTerms, int totalWords, long dictionarySizeInBytes) {
        this.documentsCount = documentsCount;
        this.uniqueTerms = uniqueTerms;
        this.totalWords = totalWords;
        this.dictionarySizeInBytes = dictionarySizeInBytes;
    }

    public void incrementDocumentsCount() {
        this.documentsCount++;
    }

    public void incrementUniqueTermsCount() {
        this.uniqueTerms++;
    }

    public void incrementTotalWordsCount() {
        this.totalWords++;
    }

    public void addSizeTotalDictionarySize(long size) {
        this.dictionarySizeInBytes += size;
    }

    public int getUniqueTerms() {
        return uniqueTerms;
    }

    public int getTotalWords() {
        return totalWords;
    }

    public long getDictionarySizeInBytes() {
        return dictionarySizeInBytes;
    }

    // TODO
    @Override
    public String toString() {
        return "DictionaryStats{" +
                "documentsCount=" + documentsCount +
                ", uniqueTerms=" + uniqueTerms +
                ", totalWords=" + totalWords +
                ", dictionarySizeInBytes=" + dictionarySizeInBytes +
                '}';
    }
}
