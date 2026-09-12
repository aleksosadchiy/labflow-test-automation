package com.mocklab.web;

/**
 * View-friendly projection of one {@link com.mocklab.domain.SampleStatus}
 * stage for the Sample Details screen (functional-spec.md §2.4): its display
 * name and whether it is {@code completed}, {@code current}, or
 * {@code remaining} relative to the sample's current status.
 */
public record SampleStageRow(String name, String state) {
}
