package index;

import compression.FrontCoding;
import compression.GammaCode;
import compression.VariableByteCode;
import enums.CompressionMethod;

import java.util.*;
public class IndexCompressor {
    private static final int BLOCK_SIZE = 64;

    public static CompressedIndex compress(InvertedIndex index, CompressionMethod method) {
        CompressedIndex compressedIndex = new CompressedIndex();
        compressedIndex.setPostingCompressionMethod(method);

        Set<String> termsSet = index.getAllTerms();
        List<String> terms = new ArrayList<>(termsSet);
        Collections.sort(terms);

        byte[] compressedDict = FrontCoding.compressToBytes(terms, BLOCK_SIZE);
        compressedIndex.setCompressedDictionary(compressedDict);

        int dictionarySize = calculateDictionarySize(terms);
        compressedIndex.setOriginalDictionarySize(dictionarySize);

        Map<Integer, byte[]> compressedPostings = new HashMap<>();
        int totalPostingsSize = 0;

        for (int i = 0; i < terms.size(); i++) {
            String term = terms.get(i);
            var docsOpt = index.getDocuments(term);

            if (docsOpt.isPresent()) {
                List<Integer> docIds = new ArrayList<>(docsOpt.get());
                Collections.sort(docIds);

                byte[] compressedPosting;
                if (method == CompressionMethod.VBC) {
                    compressedPosting = VariableByteCode.encodeWithGaps(docIds);
                } else {
                    compressedPosting = GammaCode.encodeWithGaps(docIds);
                }

                compressedPostings.put(i, compressedPosting);
                totalPostingsSize += docIds.size() * 4; // Integer = 4 bytes
            }
        }

        compressedIndex.setCompressedPostings(compressedPostings);
        compressedIndex.setOriginalPostingsSize(totalPostingsSize);

        return compressedIndex;
    }


    public static InvertedIndex decompress(CompressedIndex compressed) {
        InvertedIndex index = new InvertedIndex();

        List<String> terms = FrontCoding.decompressFromBytes(
                compressed.getCompressedDictionary()
        );

        Map<Integer, byte[]> compressedPostings = compressed.getCompressedPostings();
        CompressionMethod method = compressed.getPostingCompressionMethod();

        for (int i = 0; i < terms.size(); i++) {
            byte[] compressedPosting = compressedPostings.get(i);
            if (compressedPosting == null) continue;

            List<Integer> docIds;
            if (method == CompressionMethod.VBC) {
                docIds = VariableByteCode.decodeWithGaps(compressedPosting);
            } else {
                docIds = GammaCode.decodeWithGaps(compressedPosting);
            }

            String term = terms.get(i);
            for (int docId : docIds) {
                index.addTerm(term, docId);
            }
        }

        return index;
    }

    private static int calculateDictionarySize(List<String> terms) {
        int size = 0;
        for (String term : terms) {
            size += term.length() * 2;
        }
        return size;
    }
}
