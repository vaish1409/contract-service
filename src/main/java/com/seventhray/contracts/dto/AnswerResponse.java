package com.seventhray.contracts.dto;

import java.util.List;

public record AnswerResponse(
        String question,
        boolean found,
        String answer,
        List<Passage> passages) {

    public record Passage(int rank, double score, String text) {}
}
