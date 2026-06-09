package com.proyectomio.monolith;

import java.util.Map;

final class ProcessingResult {
    private final Map<String, ArcAccumulator> accumulators;
    private final Metrics metrics;
    private final int busesSeen;

    ProcessingResult(Map<String, ArcAccumulator> accumulators, Metrics metrics, int busesSeen) {
        this.accumulators = accumulators;
        this.metrics = metrics;
        this.busesSeen = busesSeen;
    }

    Map<String, ArcAccumulator> accumulators() {
        return accumulators;
    }

    Metrics metrics() {
        return metrics;
    }

    int busesSeen() {
        return busesSeen;
    }
}
