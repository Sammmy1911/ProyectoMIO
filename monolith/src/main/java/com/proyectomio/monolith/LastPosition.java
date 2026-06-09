package com.proyectomio.monolith;

final class LastPosition {
    final String routeId;
    final long epochSecond;
    final double latitude;
    final double longitude;
    final Arc arc;

    LastPosition(String routeId, long epochSecond, double latitude, double longitude, Arc arc) {
        this.routeId = routeId;
        this.epochSecond = epochSecond;
        this.latitude = latitude;
        this.longitude = longitude;
        this.arc = arc;
    }
}
