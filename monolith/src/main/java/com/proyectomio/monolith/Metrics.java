package com.proyectomio.monolith;

import java.util.Locale;

final class Metrics {
    long totalDatagrams;
    long validDatagrams;
    long invalidDatagrams;
    long unmatchedGps;
    long outOfOrderDatagrams;
    long anomalousSpeeds;
    long processedSegments;
    long startedNanos;
    long finishedNanos;

    double elapsedSeconds() {
        return (finishedNanos - startedNanos) / 1_000_000_000.0;
    }

    String report(int arcs, int buses) {
        double elapsed = elapsedSeconds();
        double throughput = elapsed <= 0.0 ? 0.0 : totalDatagrams / elapsed;
        return String.format(
                Locale.US,
                "mode=monolithic%n" +
                        "arcs=%d%n" +
                        "buses_seen=%d%n" +
                        "total_datagrams=%d%n" +
                        "valid_datagrams=%d%n" +
                        "invalid_datagrams=%d%n" +
                        "unmatched_gps=%d%n" +
                        "out_of_order_datagrams=%d%n" +
                        "anomalous_speeds=%d%n" +
                        "processed_segments=%d%n" +
                        "elapsed_seconds=%.6f%n" +
                        "throughput_datagrams_per_second=%.2f%n",
                arcs,
                buses,
                totalDatagrams,
                validDatagrams,
                invalidDatagrams,
                unmatchedGps,
                outOfOrderDatagrams,
                anomalousSpeeds,
                processedSegments,
                elapsed,
                throughput
        );
    }
}
