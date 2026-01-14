import annotations.NotNull;
import util.FileWalker;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ReflectionEngine {

    private static final String CLASSES_DIRECTORY = "build/classes/java/main";
    private ReflectionEngine() {
        throw new AssertionError("%s cannot be instantiated".formatted(getClass().getSimpleName()));
    }

    public static void searchForAnnotations() throws IOException, ClassNotFoundException {
        List<Path> classFiles = FileWalker.findFiles(CLASSES_DIRECTORY,
        path -> {
                String filename = path.getFileName().toString();
                return filename.endsWith(".class") && !filename.contains("NotNull");
            }
        );

        for (Path path : classFiles) {
            String className = pathToClassName(path, CLASSES_DIRECTORY);

            try {
                Class<?> clazz = Class.forName(className);

                Method[] methods = clazz.getDeclaredMethods();
                for (Method method : methods) {
                    Parameter[] parameters = method.getParameters();
                    for (Parameter parameter : parameters) {
                        Annotation[] annotations = parameter.getAnnotations();
                        for (Annotation annotation : annotations) {
                            if (annotation.annotationType().equals(NotNull.class)) {
//                                var parameterType = parameter.getType().getSimpleName();
                                if (parameter == null) {
                                    throw new IllegalArgumentException("@NotNull method is not allowed on class " + className);
                                }
                                System.out.println("NOT NULL ANNOTATION");
                            }
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

    }

    private static String pathToClassName(Path classFile, String basePath) {
        String relativePath = Paths.get(basePath).relativize(classFile).toString();
        return relativePath
                .replace(File.separator, ".")
                .replace(".class", "");
    }
}
