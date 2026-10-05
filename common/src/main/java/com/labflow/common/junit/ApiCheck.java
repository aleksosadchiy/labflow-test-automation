package com.labflow.common.junit;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Labels a workflow check: it drives the lab through its connected interface (the API).
 * Normally carried by the shared base class, so every workflow check inherits it.
 *
 * <p>Tag {@value Labels#API}. Select it with {@code ./mvnw -pl api-tests -am test -Dgroups=api}.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Tag(Labels.API)
public @interface ApiCheck {
}
