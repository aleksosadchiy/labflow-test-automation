package com.labflow.common.model;

// @layer: unit
// @spec: 003-test-automation-foundation

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for {@link SampleType}: the constant names are the lab's JSON wire
 * values, so the set must match the lab exactly (technical-considerations.md §2.3).
 *
 * @spec: 003-test-automation-foundation
 */
class SampleTypeTest {

    @Test
    // @regression
    @DisplayName("The lab supports exactly four sample types, named as on the wire")
    void theLabSupportsExactlyFourSampleTypes() {
        List<String> names = Arrays.stream(SampleType.values()).map(SampleType::name).toList();

        assertEquals(List.of("BLOOD", "TISSUE", "SWAB", "URINE"), names);
    }
}
