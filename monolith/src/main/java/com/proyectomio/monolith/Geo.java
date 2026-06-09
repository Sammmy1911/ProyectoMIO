package com.proyectomio.monolith;

final class Geo {
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    private Geo() {
    }

    static double fastDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double midLatRad = Math.toRadians((lat1 + lat2) * 0.5);
        double x = (lon2 - lon1) * METERS_PER_DEGREE_LAT * Math.cos(midLatRad);
        double y = (lat2 - lat1) * METERS_PER_DEGREE_LAT;
        return Math.sqrt(x * x + y * y);
    }

    static double distancePointToSegmentMeters(double lat, double lon, Arc arc) {
        double cos = Math.cos(Math.toRadians(arc.midLat));
        double px = lon * METERS_PER_DEGREE_LAT * cos;
        double py = lat * METERS_PER_DEGREE_LAT;
        double ax = arc.fromLon * METERS_PER_DEGREE_LAT * cos;
        double ay = arc.fromLat * METERS_PER_DEGREE_LAT;
        double bx = arc.toLon * METERS_PER_DEGREE_LAT * cos;
        double by = arc.toLat * METERS_PER_DEGREE_LAT;

        double abx = bx - ax;
        double aby = by - ay;
        double apx = px - ax;
        double apy = py - ay;
        double ab2 = abx * abx + aby * aby;
        if (ab2 == 0.0) {
            double dx = px - ax;
            double dy = py - ay;
            return Math.sqrt(dx * dx + dy * dy);
        }

        double t = (apx * abx + apy * aby) / ab2;
        if (t < 0.0) {
            t = 0.0;
        } else if (t > 1.0) {
            t = 1.0;
        }
        double closestX = ax + t * abx;
        double closestY = ay + t * aby;
        double dx = px - closestX;
        double dy = py - closestY;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
