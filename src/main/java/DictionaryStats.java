public record DictionaryStats(int documentsCount, int uniqueTerms, int totalWords, long collectionSizeInBytes) {

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
