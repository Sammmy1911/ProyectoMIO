package com.proyectomio.monolith;

final class MiniPilotDatagramParser implements DatagramParser {
    private static final double COORDINATE_SCALE = 10_000_000.0;

    @Override
    public Datagram parse(String line) {
        try {
            String[] p = Csv.split(line);
            if (p.length < 11) {
                return null;
            }

            String busId = p[2];
            String routeId = p[7];
            double lat = Double.parseDouble(p[4]) / COORDINATE_SCALE;
            double lon = Double.parseDouble(p[5]) / COORDINATE_SCALE;
            if (busId.isBlank() || !validCoordinates(lat, lon)) {
                return null;
            }
            return new Datagram(busId, routeId, TimestampParser.parseEpochSecond(p[10]), lat, lon);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean validCoordinates(double lat, double lon) {
        return lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0;
    }
}
