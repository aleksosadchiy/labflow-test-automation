package com.labflow.common.junit;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Labels a check as part of the full regression run, which is every workflow and screen check.
 * Normally carried by the shared base classes.
 *
 * <p>Tag {@value Labels#REGRESSION}. Select it with {@code ./mvnw test -Dgroups=regression}.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Tag(Labels.REGRESSION)
public @interface Regression {
}
