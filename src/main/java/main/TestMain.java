package main;

import compression.FrontCoding;
import compression.VariableByteCode;
import core.BooleanSearchEngine;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static compression.GammaCode.encodeNumber;
import static constants.Filenames.DIRECTORY_PATH;

public class TestMain {
    public static void main(String[] args) {
        System.out.println("Testing encodeNumber():");
        System.out.println("1  → " + encodeNumber(1));    // Expected: "1"
        System.out.println("2  → " + encodeNumber(2));    // Expected: "010"
        System.out.println("3  → " + encodeNumber(3));    // Expected: "011"
        System.out.println("4  → " + encodeNumber(4));    // Expected: "00100"
        System.out.println("5  → " + encodeNumber(5));    // Expected: "00101"
        System.out.println("10 → " + encodeNumber(10));   // Expected: "0001010"
        System.out.println("23 → " + encodeNumber(23));   // Expected: "000010111"
    }


}
