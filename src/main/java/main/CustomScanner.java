package main;

import java.util.InputMismatchException;
import java.util.Scanner;

public class CustomScanner {
    private final Scanner scanner;

    public CustomScanner(Scanner scanner) {
        if (scanner == null) {
            throw new IllegalArgumentException("scanner cannot be null");
        }
        this.scanner = scanner;
    }

    public int parseInt() throws InputMismatchException {
        try {
            int result = scanner.nextInt();
            scanner.nextLine();
            return result;
        } catch (InputMismatchException e) {
            scanner.nextLine();
            return -1;
        }
    }

    public String parseString() {
        return scanner.nextLine().trim();
    }

    public void close() {
        scanner.close();
    }
}
