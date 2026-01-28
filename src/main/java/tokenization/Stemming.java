//package tokenization;
//
//import java.util.List;
//
//public class Stemming {
//    private static final List<Character> vowels = List.of('a', 'e', 'i', 'o', 'u');
//    private static final List<Character> consonants = List.of( 'b', 'c', 'd', 'f', 'g', 'h', 'j',
//            'k', 'l', 'm', 'n', 'p', 'q', 'r', 's', 't', 'v', 'w', 'x', 'z');
//
//    private Stemming() {
//        throw new UnsupportedOperationException("Utility class");
//    }
//
//
//    public static int measure(String input) {
//        boolean vowel = false;
//        boolean consonant = false;
//
//        int count = 0;
//
//        for (char c : input.toCharArray()) {
//            if (vowel && consonant) {
//                vowel = false;
//                consonant = false;
//                count++;
//            }
//
//            if (vowels.contains(c)) {
//                vowel = true;
//                consonant = false;
//            } else if (consonants.contains(c)) {
//                consonant = true;
//                vowel = false;
//            }
//        }
//
//        return count;
//    }
//
//    public static String step1(String word) {
//        if (word.endsWith("sses") || word.endsWith("ies")) {
//            return word.substring(0, word.length() - 2);
//        }
//        if (word.endsWith("s")) {
//            return word.substring(0, word.length() - 1);
//        }
//        return word;
//    }
//}
