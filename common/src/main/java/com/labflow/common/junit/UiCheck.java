package com.labflow.common.junit;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Labels a screen check: it drives the lab's screens in a browser.
 * Normally carried by the shared base class, so every screen check inherits it.
 *
 * <p>Tag {@value Labels#UI}. Select it with {@code ./mvnw -pl ui-tests -am test -Dgroups=ui}.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Tag(Labels.UI)
public @interface UiCheck {
}
