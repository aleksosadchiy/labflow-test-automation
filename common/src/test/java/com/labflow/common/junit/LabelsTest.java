package com.labflow.common.junit;

// @layer: unit
// @spec: 005-reliable-isolated-execution

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.support.AnnotationSupport;

import java.lang.reflect.AnnotatedElement;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the label annotations, per spec 005 technical-considerations.md §2.2: each
 * label resolves to its JUnit tag, and a label on a base class reaches every check that extends
 * it (so the base classes can carry {@code api}/{@code ui} and {@code regression} once).
 *
 * <p>Inheritance is checked two ways. JUnit's own tag lookup ({@link AnnotationSupport}) walks
 * superclasses even without {@code @Inherited} on the label, because JUnit's {@code @Tags}
 * container is itself inherited. Plain Java reflection ({@link Class#isAnnotationPresent}), which
 * a guard or listener may use to ask "is this an api check?", sees a label on a subclass only
 * when the label is {@code @Inherited}.
 *
 * @spec: 005-reliable-isolated-execution
 */
class LabelsTest {

    @ApiCheck
    @Regression
    abstract static class ApiBase {
    }

    static class ApiChild extends ApiBase {
        @Smoke
        void keyPath() {
        }
    }

    @UiCheck
    static class UiBase {
    }

    static class UiChild extends UiBase {
    }

    @Smoke
    static class SmokeBase {
    }

    static class SmokeChild extends SmokeBase {
    }

    private static Set<String> tagsOf(AnnotatedElement element) {
        List<Tag> tags = AnnotationSupport.findRepeatableAnnotations(element, Tag.class);
        return tags.stream().map(Tag::value).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("The tag names are api, ui, smoke and regression, as typed in -Dgroups")
    void tagNamesAreAsTypedOnTheCommandLine() {
        assertEquals(List.of("api", "ui", "smoke", "regression"),
                List.of(Labels.API, Labels.UI, Labels.SMOKE, Labels.REGRESSION));
    }

    @Test
    @DisplayName("A base class labelled api and regression carries both tags")
    void apiAndRegressionLabelsResolveToTheirTags() {
        assertEquals(Set.of("api", "regression"), tagsOf(ApiBase.class));
    }

    @Test
    @DisplayName("A check extending the api base class inherits the api and regression tags")
    void apiAndRegressionLabelsAreInherited() {
        assertEquals(Set.of("api", "regression"), tagsOf(ApiChild.class));
        assertTrue(ApiChild.class.isAnnotationPresent(ApiCheck.class), "ApiCheck is not @Inherited");
        assertTrue(ApiChild.class.isAnnotationPresent(Regression.class), "Regression is not @Inherited");
    }

    @Test
    @DisplayName("A check extending the ui base class inherits the ui tag")
    void uiLabelResolvesAndIsInherited() {
        assertEquals(Set.of("ui"), tagsOf(UiBase.class));
        assertEquals(Set.of("ui"), tagsOf(UiChild.class));
        assertTrue(UiChild.class.isAnnotationPresent(UiCheck.class), "UiCheck is not @Inherited");
    }

    @Test
    @DisplayName("The smoke label on a check method resolves to the smoke tag")
    void smokeLabelOnAMethodResolvesToItsTag() throws NoSuchMethodException {
        assertEquals(Set.of("smoke"), tagsOf(ApiChild.class.getDeclaredMethod("keyPath")));
    }

    @Test
    @DisplayName("The smoke label on a class reaches a check that extends it")
    void smokeLabelOnAClassIsInherited() {
        assertEquals(Set.of("smoke"), tagsOf(SmokeChild.class));
        assertTrue(SmokeChild.class.isAnnotationPresent(Smoke.class), "Smoke is not @Inherited");
    }
}
