package com.proyectomio.monolith;

import java.util.Locale;

final class ArcAccumulator {
    private long samples;
    private double speedKmhSum;
    private double minSpeedKmh = Double.POSITIVE_INFINITY;
    private double maxSpeedKmh = Double.NEGATIVE_INFINITY;
    private double distanceMetersSum;
    private double durationSecondsSum;

    void add(double speedKmh, double distanceMeters, double durationSeconds) {
        samples++;
        speedKmhSum += speedKmh;
        distanceMetersSum += distanceMeters;
        durationSecondsSum += durationSeconds;
        if (speedKmh < minSpeedKmh) {
            minSpeedKmh = speedKmh;
        }
        if (speedKmh > maxSpeedKmh) {
            maxSpeedKmh = speedKmh;
        }
    }

    long samples() {
        return samples;
    }

    double averageSpeedKmh() {
        return samples == 0 ? 0.0 : speedKmhSum / samples;
    }

    String toCsv(Arc arc, long minSamples) {
        String status = samples >= minSamples ? "OK" : "SIN_DATOS_SUFICIENTES";
        double min = samples == 0 ? 0.0 : minSpeedKmh;
        double max = samples == 0 ? 0.0 : maxSpeedKmh;
        return String.format(
                Locale.US,
                "%s,%s,%.6f,%.6f,%.6f,%.6f,%.2f,%d,%.4f,%.4f,%.4f,%.2f,%.2f,%s",
                arc.id,
                arc.routeId,
                arc.fromLat,
                arc.fromLon,
                arc.toLat,
                arc.toLon,
                arc.lengthMeters,
                samples,
                averageSpeedKmh(),
                min,
                max,
                distanceMetersSum,
                durationSecondsSum,
                status
        );
    }
}
