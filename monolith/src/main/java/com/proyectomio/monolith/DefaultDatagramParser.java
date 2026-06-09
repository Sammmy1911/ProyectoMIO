package com.proyectomio.monolith;

final class DefaultDatagramParser implements DatagramParser {
    @Override
    public Datagram parse(String line) {
        try {
            String[] p = Csv.split(line);
            if (p.length < 5 || p[0].isBlank() || p[2].isBlank()) {
                return null;
            }
            double lat = Double.parseDouble(p[3]);
            double lon = Double.parseDouble(p[4]);
            if (!validCoordinates(lat, lon)) {
                return null;
            }
            return new Datagram(p[0], p[1], TimestampParser.parseEpochSecond(p[2]), lat, lon);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean validCoordinates(double lat, double lon) {
        return lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0;
    }
}
