package main;

import core.BooleanSearchEngine;
import lombok.extern.slf4j.Slf4j;
import menu.MenuController;
import scanner.CustomScanner;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.OptionalInt;
import java.util.Scanner;

import static menu.Printer.printMenu;

@Slf4j
public class Main {
    static {
        System.setProperty("slf4j.internal.verbosity", "WARN");
    }

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        log.info("=".repeat(80));
        log.info("APPLICATION STARTED at {}", LocalDateTime.now());
        log.info("=".repeat(80));

        BooleanSearchEngine searchEngine = new BooleanSearchEngine();
        Scanner scanner = new Scanner(System.in);
        CustomScanner customScanner = new CustomScanner(scanner);
        MenuController controller = new MenuController(searchEngine, customScanner);

        boolean running = true;
        while (running) {
            printMenu(searchEngine.getCurrentMode());
            try {
                OptionalInt choice = customScanner.parseInt();
                if (choice.isEmpty() || choice.getAsInt() < 0) {
                    System.out.println("Invalid input");
                    continue;
                }

                running = controller.handleUserChoice(choice.getAsInt());
            } catch (Exception e) {
                log.error("Unexpected error occurred", e);
                System.err.println("   An error occurred: " + e.getMessage());
                System.err.println("   Please try again\n.");
            }
        }

        customScanner.close();
        log.info("=".repeat(80));
        log.info("APPLICATION FINISHED at {}", LocalDateTime.now());
        log.info("=".repeat(80));
    }
}
