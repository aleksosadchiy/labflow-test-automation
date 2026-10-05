package com.labflow.common.junit;

import org.junit.platform.commons.JUnitException;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.support.hierarchical.ParallelExecutionConfiguration;
import org.junit.platform.engine.support.hierarchical.ParallelExecutionConfigurationStrategy;

import java.util.concurrent.ForkJoinPool;
import java.util.function.Predicate;

/**
 * Decides how many checks run at the same time (spec 005 FS §2.3): {@code -DchecksAtOnce=N}, or
 * as many as the machine has processor cores when it is not set.
 *
 * <p>Plugged in with {@code junit.jupiter.execution.parallel.config.strategy=custom} and
 * {@code junit.jupiter.execution.parallel.config.custom.class=com.labflow.common.junit.ChecksAtOnceStrategy};
 * JUnit creates it through the public no-argument constructor. A custom strategy is used because
 * JUnit's {@code fixed} strategy cannot default to the core count and {@code dynamic} cannot take
 * an exact number.
 *
 * <p><b>Where the value comes from.</b> Jupiter gives this strategy a view of its configuration
 * parameters in which every key is prefixed with {@code junit.jupiter.execution.parallel.config.},
 * so {@code get("checksAtOnce")} looks up {@code junit.jupiter.execution.parallel.config.checksAtOnce}
 * (from {@code junit-platform.properties}, an explicit launcher parameter or a JVM system property
 * of that long name). The documented short option {@code -DchecksAtOnce} is a plain JVM system
 * property - Surefire passes it to the test JVM like {@code -DbaseUrl} - and is read directly when
 * the long key is not set.
 *
 * <p><b>Why "no more than N" holds.</b> The worker pool gets parallelism, core size and maximum
 * size all equal to N, so it never has more than N threads, and each thread runs one check at a
 * time. The saturate predicate returns {@code true}: when a worker blocks and the pool is already
 * at its maximum, the pool carries on with the threads it has instead of failing. Keep-alive is
 * JUnit's own default of 30 seconds, and the minimum number of runnable threads is N, as in
 * JUnit's {@code fixed} strategy.
 */
public class ChecksAtOnceStrategy implements ParallelExecutionConfigurationStrategy {

    /** The option name: {@code -DchecksAtOnce=N}. */
    public static final String CHECKS_AT_ONCE = "checksAtOnce";

    /** Same idle-thread keep-alive as JUnit's built-in strategies. */
    private static final int KEEP_ALIVE_SECONDS = 30;

    @Override
    public ParallelExecutionConfiguration createConfiguration(ConfigurationParameters configurationParameters) {
        String value = configurationParameters.get(CHECKS_AT_ONCE)
                .orElseGet(() -> System.getProperty(CHECKS_AT_ONCE));
        return new AtMost(checksAtOnce(value));
    }

    /** Unset or blank means one per processor core; otherwise a whole number of at least 1. */
    static int checksAtOnce(String value) {
        if (value == null || value.isBlank()) {
            return Runtime.getRuntime().availableProcessors();
        }
        try {
            int n = Integer.parseInt(value.trim());
            if (n >= 1) {
                return n;
            }
        } catch (NumberFormatException notAWholeNumber) {
            // falls through to the same message as zero or a negative number
        }
        throw new JUnitException("checksAtOnce must be a positive whole number, got '" + value + "'");
    }

    /** A pool of exactly {@code n} worker threads that never adds more. */
    private record AtMost(int n) implements ParallelExecutionConfiguration {

        @Override
        public int getParallelism() {
            return n;
        }

        @Override
        public int getMinimumRunnable() {
            return n;
        }

        @Override
        public int getMaxPoolSize() {
            return n;
        }

        @Override
        public int getCorePoolSize() {
            return n;
        }

        @Override
        public int getKeepAliveSeconds() {
            return KEEP_ALIVE_SECONDS;
        }

        @Override
        public Predicate<? super ForkJoinPool> getSaturatePredicate() {
            return pool -> true;
        }
    }
}
