package com.proyectomio.monolith;

final class Arc {
    final String id;
    final String routeId;
    final double fromLat;
    final double fromLon;
    final double toLat;
    final double toLon;
    final double midLat;
    final double minLat;
    final double maxLat;
    final double minLon;
    final double maxLon;
    final double lengthMeters;

    Arc(String id, String routeId, double fromLat, double fromLon, double toLat, double toLon) {
        this.id = id;
        this.routeId = routeId;
        this.fromLat = fromLat;
        this.fromLon = fromLon;
        this.toLat = toLat;
        this.toLon = toLon;
        this.midLat = (fromLat + toLat) * 0.5;
        this.minLat = Math.min(fromLat, toLat);
        this.maxLat = Math.max(fromLat, toLat);
        this.minLon = Math.min(fromLon, toLon);
        this.maxLon = Math.max(fromLon, toLon);
        this.lengthMeters = Geo.fastDistanceMeters(fromLat, fromLon, toLat, toLon);
    }
}
