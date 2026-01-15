package annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Loggable {
    String message() default "";
    LoggingLevel level() default LoggingLevel.INFO;

    enum LoggingLevel {
        DEBUG, INFO, WARN, ERROR
    }
}
