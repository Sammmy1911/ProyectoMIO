package com.proyectomio.monolith;

final class Datagram {
    final String busId;
    final String routeId;
    final long epochSecond;
    final double latitude;
    final double longitude;

    Datagram(String busId, String routeId, long epochSecond, double latitude, double longitude) {
        this.busId = busId;
        this.routeId = routeId;
        this.epochSecond = epochSecond;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
