package com.labflow.testdata;

// @layer: unit
// @spec: 005-reliable-isolated-execution

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link UniqueData}, per spec 005 functional-spec.md §2.4: every generated value
 * is unique and starts with the fixed {@code TEST-} prefix.
 *
 * @spec: 005-reliable-isolated-execution
 */
class UniqueDataTest {

    @Test
    // @regression
    @DisplayName("Many generated names are all different")
    void manyNamesAreAllDifferent() {
        Set<String> names = new HashSet<>();
        IntStream.range(0, 1_000).forEach(i -> names.add(UniqueData.name("patient")));

        assertEquals(1_000, names.size());
    }

    @Test
    // @regression
    @DisplayName("A generated name starts with the test prefix and then the kind")
    void nameStartsWithTestPrefixAndKind() {
        String name = UniqueData.name("patient");

        assertEquals("TEST-", UniqueData.TEST_PREFIX);
        assertTrue(name.startsWith("TEST-patient-"), name);
        assertTrue(name.matches("TEST-patient-[0-9a-f-]{36}"), name);
    }
}
