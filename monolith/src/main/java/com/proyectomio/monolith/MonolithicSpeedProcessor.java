package com.proyectomio.monolith;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class MonolithicSpeedProcessor {
    private final Graph graph;
    private final DatagramParser parser;
    private final double maxGpsDistanceMeters;
    private final double maxSpeedKmh;
    private final long minSegmentSeconds;
    private final long maxSegmentSeconds;
    private final long minSamplesPerArc;
    private final boolean ignoreRoute;
    private final long limit;

    MonolithicSpeedProcessor(
            Graph graph,
            DatagramParser parser,
            double maxGpsDistanceMeters,
            double maxSpeedKmh,
            long minSegmentSeconds,
            long maxSegmentSeconds,
            long minSamplesPerArc,
            boolean ignoreRoute,
            long limit
    ) {
        this.graph = graph;
        this.parser = parser;
        this.maxGpsDistanceMeters = maxGpsDistanceMeters;
        this.maxSpeedKmh = maxSpeedKmh;
        this.minSegmentSeconds = minSegmentSeconds;
        this.maxSegmentSeconds = maxSegmentSeconds;
        this.minSamplesPerArc = minSamplesPerArc;
        this.ignoreRoute = ignoreRoute;
        this.limit = limit;
    }

    ProcessingResult process(Path datagramsPath) throws IOException {
        Metrics metrics = new Metrics();
        Map<String, LastPosition> lastByBus = new HashMap<>(65_536);
        Map<String, ArcAccumulator> accumulators = new HashMap<>(graph.arcs().size() * 2);
        for (Arc arc : graph.arcs()) {
            accumulators.put(arc.id, new ArcAccumulator());
        }

        metrics.startedNanos = System.nanoTime();
        try (BufferedReader reader = Files.newBufferedReader(datagramsPath, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("El archivo de datagramas está vacío: " + datagramsPath);
            }

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (limit >= 0 && metrics.totalDatagrams >= limit) {
                    break;
                }
                metrics.totalDatagrams++;
                Datagram datagram = parser.parse(line);
                if (datagram == null) {
                    metrics.invalidDatagrams++;
                    continue;
                }
                metrics.validDatagrams++;

                Arc currentArc = graph.findNearestArc(
                        datagram.routeId,
                        datagram.latitude,
                        datagram.longitude,
                        maxGpsDistanceMeters,
                        ignoreRoute
                );
                if (currentArc == null) {
                    metrics.unmatchedGps++;
                    continue;
                }

                LastPosition previous = lastByBus.put(datagram.busId, new LastPosition(
                        datagram.routeId,
                        datagram.epochSecond,
                        datagram.latitude,
                        datagram.longitude,
                        currentArc
                ));
                if (previous == null) {
                    continue;
                }

                long durationSeconds = datagram.epochSecond - previous.epochSecond;
                if (durationSeconds < minSegmentSeconds) {
                    metrics.outOfOrderDatagrams++;
                    continue;
                }
                if (durationSeconds > maxSegmentSeconds) {
                    continue;
                }
                if (!previous.routeId.equals(datagram.routeId)) {
                    continue;
                }

                Arc segmentArc = previous.arc.id.equals(currentArc.id) ? currentArc : chooseCloserArc(previous, datagram, currentArc);
                double distanceMeters = Geo.fastDistanceMeters(
                        previous.latitude,
                        previous.longitude,
                        datagram.latitude,
                        datagram.longitude
                );
                double speedKmh = (distanceMeters / durationSeconds) * 3.6;
                if (speedKmh <= 0.0 || speedKmh > maxSpeedKmh) {
                    metrics.anomalousSpeeds++;
                    continue;
                }

                accumulators.get(segmentArc.id).add(speedKmh, distanceMeters, durationSeconds);
                metrics.processedSegments++;
            }
        } finally {
            metrics.finishedNanos = System.nanoTime();
        }

        // Touch the threshold here so experimental configs are reflected by the output writer.
        if (minSamplesPerArc < 1) {
            throw new IllegalArgumentException("minSamplesPerArc debe ser mayor o igual a 1");
        }
        return new ProcessingResult(accumulators, metrics, lastByBus.size());
    }

    private Arc chooseCloserArc(LastPosition previous, Datagram current, Arc currentArc) {
        double previousDistanceToCurrent = Geo.distancePointToSegmentMeters(previous.latitude, previous.longitude, currentArc);
        double currentDistanceToPrevious = Geo.distancePointToSegmentMeters(current.latitude, current.longitude, previous.arc);
        return previousDistanceToCurrent < currentDistanceToPrevious ? currentArc : previous.arc;
    }

}
