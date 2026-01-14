public class DictionaryStats {
    private int documentsCount;
    private int uniqueTerms;
    private int totalWords;
    private long collectionSizeInBytes;

    public DictionaryStats(int documentsCount, int uniqueTerms, int totalWords, long collectionSizeInBytes) {
        this.documentsCount = documentsCount;
        this.uniqueTerms = uniqueTerms;
        this.totalWords = totalWords;
        this.collectionSizeInBytes = collectionSizeInBytes;
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

    public void addFileSize(long size) {
        this.collectionSizeInBytes += size;
    }

    public int getUniqueTerms() {
        return uniqueTerms;
    }

    public int getTotalWords() {
        return totalWords;
    }

    public long getCollectionSizeInBytes() {
        return collectionSizeInBytes;
    }

    // TODO
    @Override
    public String toString() {
        return "DictionaryStats{" +
                "documentsCount=" + documentsCount +
                ", uniqueTerms=" + uniqueTerms +
                ", totalWords=" + totalWords +
                ", collectionSizeInBytes=" + collectionSizeInBytes +
                '}';
    }
}
