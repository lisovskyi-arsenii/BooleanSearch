package aspect;

import annotations.Loggable;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;


@Aspect
public class LoggingAspect {

    @Around("@annotation(loggable)")
    public Object logMethodExecution(ProceedingJoinPoint joinPoint, Loggable loggable) throws Throwable {
        Logger logger = LoggerFactory.getLogger(joinPoint.getTarget().getClass());
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getName();
        String className = signature.getDeclaringTypeName();
        Object[] args = joinPoint.getArgs();

        long startTime = System.currentTimeMillis();
        String argsStr = args != null ? Arrays.toString(args) : "[]";

        logMessage(logger, loggable.level(),
            "-> Entering {}.{}({})",
                className.substring(className.lastIndexOf('.') + 1),
                methodName,
                argsStr
        );

        try {
            Object result = joinPoint.proceed(args);

            long duration = System.currentTimeMillis() - startTime;
            logMessage(logger, loggable.level(),
                    "<- Exiting {}.{}() = {} ({} ms)",
                    className.substring(className.lastIndexOf('.') + 1),
                    methodName,
                    result,
                    duration
            );

            return result;
        } catch (Throwable e) {
            logger.error("✗ Exception in {}.{}(): {}",
                    className.substring(className.lastIndexOf('.') + 1),
                    methodName,
                    e.getMessage());
            throw e;
        }
    }

    private void logMessage(Logger logger, Loggable.LoggingLevel level, String message, Object... args) {
        switch (level) {
            case DEBUG ->  logger.debug(message, args);
            case INFO -> logger.info(message, args);
            case WARN -> logger.warn(message, args);
            case ERROR -> logger.error(message, args);
        }
    }
}
