//import util.FileWalker;
//
//import java.io.File;
//import java.io.IOException;
//import java.lang.reflect.Method;
//import java.lang.reflect.Parameter;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.List;
//
//public class ReflectionEngine {
//
//    private static final String CLASSES_DIRECTORY = "build/classes/java/main";
//    private ReflectionEngine() {
//        throw new AssertionError("%s cannot be instantiated".formatted(getClass().getSimpleName()));
//    }
//
//    public static void searchForAnnotations() throws IOException, ClassNotFoundException {
//        List<Path> classFiles = FileWalker.findFiles(CLASSES_DIRECTORY,
//        path -> {
//                String filename = path.getFileName().toString();
//                return filename.endsWith(".class") && !filename.contains("NotNull");
//            }
//        );
//
//        System.out.println("=== Scanning for @NotNull annotations ===\n");
//        int foundCount = 0;
//
//        for (Path path : classFiles) {
//            String className = pathToClassName(path, CLASSES_DIRECTORY);
//            Class<?> clazz = Class.forName(className);
//
//            for (Method method : clazz.getDeclaredMethods()) {
//                for (Parameter parameter : method.getParameters()) {
//                    if (parameter.isAnnotationPresent(NotNull.class)) {
//                        foundCount++;
//
//                        System.out.printf("Found @NotNull:%n");
//                        System.out.printf("  Class:     %s%n", className);
//                        System.out.printf("  Method:    %s%n", method.getName());
//                        System.out.printf("  Parameter: %s (type: %s)%n",
//                                parameter.getName(),
//                                parameter.getType().getSimpleName());
//                        System.out.printf("  Message:   %s%n%n",
//                                parameter.getAnnotation(NotNull.class).message());
//                    }
//                }
//            }
//        }
//    }
//
//
//    private static String pathToClassName(Path classFile, String basePath) {
//        String relativePath = Paths.get(basePath).relativize(classFile).toString();
//        return relativePath
//                .replace(File.separator, ".")
//                .replace(".class", "");
//    }
//}
