package com.mocklab.api.dto;

import com.mocklab.service.ProgressView;

import java.util.List;

/**
 * A sample's progress as returned by the API: the current stage and every stage
 * reached so far, oldest first and including the current one. Stages are sent as
 * their names ({@code ACCESSIONED}) and times as ISO-8601 instants.
 */
public record ProgressResponse(String sampleId, String currentStatus, List<StageEntry> stages) {

    /** One stage the sample has reached, and when. */
    public record StageEntry(String status, String reachedAt) {
    }

    public static ProgressResponse from(ProgressView view) {
        return new ProgressResponse(
                view.sampleId(),
                view.currentStatus().name(),
                view.stages().stream()
                        .map(stage -> new StageEntry(stage.status().name(), stage.reachedAt().toString()))
                        .toList());
    }
}
