package com.labflow.common.junit;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Labels one of the few key paths (the main successful journeys plus one refusal) that give
 * quick confidence the lab works. Put on individual check methods.
 *
 * <p>Tag {@value Labels#SMOKE}. Select it with {@code ./mvnw test -Dgroups="smoke | !(api | ui)"}.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Tag(Labels.SMOKE)
public @interface Smoke {
}
