package co.edu.icesi.sitmmio.visualizer.model;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class BusModel {
    private final Map<Integer, BusState> busPositions = new ConcurrentHashMap<>();

    public static class BusState {
        public double lat, lon;
        public int lineId;
        public long timestamp;
        public long lastUpdate;

        public BusState(double lat, double lon, int lineId, long timestamp) {
            this.lat = lat;
            this.lon = lon;
            this.lineId = lineId;
            this.timestamp = timestamp;
            this.lastUpdate = System.currentTimeMillis();
        }
    }

    public void updateBus(int busId, double lat, double lon, int lineId, long timestamp) {
        busPositions.compute(busId, (id, current) -> {
            if (current == null || timestamp > current.timestamp) {
                return new BusState(lat, lon, lineId, timestamp);
            }
            return current;
        });
    }

    public Map<Integer, BusState> getBusPositions() {
        return busPositions;
    }
}
