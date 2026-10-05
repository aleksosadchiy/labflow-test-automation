package com.labflow.common.junit;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.slf4j.MDC;

import java.lang.reflect.Method;

/**
 * Puts the running check's name into every log line it causes, so an engineer can find all the
 * lines of one failed check by searching for its name (spec 005 FS §2.6).
 *
 * <p>Around each test method, parameterized row, {@code @BeforeEach} and {@code @AfterEach}
 * method it sets the SLF4J MDC key {@value #MDC_KEY} to {@code <TestClass> › <display name>},
 * runs the method, and then puts back whatever was there before (removing the key if nothing
 * was). A log pattern picks it up with {@code %X{check}}.
 *
 * <p>The name is the class's simple name plus the invocation's own display name. For a
 * parameterized row that is the row's name (for example {@code BLANK}), not the method's
 * {@code @DisplayName}: it is exactly the name Surefire reports for that row, so the name seen
 * in a failure report is the name to search the log for, and the class name keeps it unique.
 *
 * <p><b>Why an {@link InvocationInterceptor} and not a {@code BeforeEachCallback}?</b> The MDC
 * is thread-local, so it must be set on the thread that actually runs the check body. With
 * {@code junit.jupiter.execution.timeout.thread.mode.default=SEPARATE_THREAD} (used by
 * {@code ui-tests}) JUnit runs the body on a helper thread, while extension callbacks such as
 * {@code beforeEach} stay on the worker thread, so an MDC value set there would never reach the
 * body. JUnit's own timeout interceptor wraps the user-registered interceptors, so the
 * {@code invocation.proceed()} below - and the MDC put just before it - runs on that helper
 * thread. For the same reason, thread-local MDC stays correct under parallel execution: each
 * worker thread carries only its own check's name. {@code CheckNameInterceptorTest} proves both
 * the default and the {@code SEPARATE_THREAD} case.
 *
 * <p>Registered with {@code @ExtendWith(CheckNameInterceptor.class)}, so it needs a public
 * no-argument constructor and keeps no state of its own.
 */
public class CheckNameInterceptor implements InvocationInterceptor {

    /** The MDC key holding the check name; a log pattern reads it as {@code %X{check}}. */
    public static final String MDC_KEY = "check";

    @Override
    public void interceptTestMethod(Invocation<Void> invocation,
                                    ReflectiveInvocationContext<Method> invocationContext,
                                    ExtensionContext extensionContext) throws Throwable {
        withCheckName(invocation, extensionContext);
    }

    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation,
                                            ReflectiveInvocationContext<Method> invocationContext,
                                            ExtensionContext extensionContext) throws Throwable {
        withCheckName(invocation, extensionContext);
    }

    @Override
    public void interceptBeforeEachMethod(Invocation<Void> invocation,
                                          ReflectiveInvocationContext<Method> invocationContext,
                                          ExtensionContext extensionContext) throws Throwable {
        withCheckName(invocation, extensionContext);
    }

    @Override
    public void interceptAfterEachMethod(Invocation<Void> invocation,
                                         ReflectiveInvocationContext<Method> invocationContext,
                                         ExtensionContext extensionContext) throws Throwable {
        withCheckName(invocation, extensionContext);
    }

    /** The name written to the MDC: {@code <TestClass simple name> › <display name>}. */
    static String checkName(ExtensionContext extensionContext) {
        return extensionContext.getRequiredTestClass().getSimpleName() + " › " + extensionContext.getDisplayName();
    }

    private static void withCheckName(Invocation<Void> invocation, ExtensionContext extensionContext) throws Throwable {
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, checkName(extensionContext));
        try {
            invocation.proceed();
        } finally {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }
}
