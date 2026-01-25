package main;

import core.BooleanSearchEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.OptionalInt;
import java.util.Scanner;

import static main.Printer.printMenu;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    static {
        System.setProperty("slf4j.internal.verbosity", "WARN");
    }

    public static void main(String[] args) {
        LOGGER.info("=".repeat(80));
        LOGGER.info("APPLICATION STARTED at {}", LocalDateTime.now());
        LOGGER.info("=".repeat(80));

        BooleanSearchEngine searchEngine = new BooleanSearchEngine();
        Scanner scanner = new Scanner(System.in);
        CustomScanner customScanner = new CustomScanner(scanner);
        MenuController controller = new MenuController(searchEngine, customScanner);

        boolean running = true;
        while (running) {
            printMenu();
            try {
                OptionalInt choice = customScanner.parseInt();
                if (choice.isEmpty()) {
                    System.out.println("Invalid input");
                    continue;
                }

                running = controller.handleUserChoice(choice.getAsInt());
            } catch (Exception e) {
                LOGGER.error("Unexpected error occurred", e);
                System.err.println("   An error occurred: " + e.getMessage());
                System.err.println("   Please try again\n.");
            }
        }

        customScanner.close();
        LOGGER.info("=".repeat(80));
        LOGGER.info("APPLICATION FINISHED at {}", LocalDateTime.now());
        LOGGER.info("=".repeat(80));
    }
}
