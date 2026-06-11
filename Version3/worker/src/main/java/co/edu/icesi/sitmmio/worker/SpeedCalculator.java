package co.edu.icesi.sitmmio.worker;

import co.edu.uicesi.sitmmio.datacenter.BusEvent;
import co.edu.uicesi.sitmmio.datacenter.ProcessingResult;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public class SpeedCalculator {

    private static final int IDX_LATITUDE      = 4;
    private static final int IDX_LONGITUDE     = 5;
    private static final int IDX_LINE_ID       = 7;
    private static final int IDX_TRIP_ID       = 8;
    private static final int IDX_DATAGRAM_DATE = 10;
    private static final int IDX_BUS_ID        = 11;

    /**
     * Versión 4.0 – Zero-String Optimization.
     * Procesa un bloque de bytes directamente convirtiéndolo a String una sola vez.
     */
    public ProcessingResult processByteChunk(byte[] data, Set<Integer> activeLineIds) {
        String content = new String(data);
        String[] lines = content.split("\n");
        
        List<BusEvent> events = new ArrayList<>(lines.length);

        for (String line : lines) {
            BusEvent event = parseLine(line, activeLineIds);
            if (event != null) {
                events.add(event);
            }
        }

        Map<String, List<BusEvent>> trajectories = new HashMap<>();
        for (BusEvent e : events) {
            String key = e.busId + "_" + e.tripId;
            trajectories.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }

        Map<String, Double> partialMonthly = new HashMap<>();
        Map<String, Double> partialSamples = new HashMap<>();

        for (List<BusEvent> trajectory : trajectories.values()) {
            trajectory.sort(Comparator.comparingLong(e -> e.timestamp));

            for (int i = 1; i < trajectory.size(); i++) {
                BusEvent curr = trajectory.get(i);
                BusEvent prev = trajectory.get(i - 1);

                double distKm = haversine(prev.latitude, prev.longitude, curr.latitude, curr.longitude);
                double hours = (curr.timestamp - prev.timestamp) / 3600000.0;

                if (hours <= 0 || distKm <= 0 || distKm > 1.0 || (distKm / hours) > 120) continue;

                double speed = distKm / hours;

                LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(curr.timestamp), ZoneId.systemDefault());
                String monthKey = curr.lineId + "_" + date.getMonthValue() + "_" + date.getYear();
                
                partialMonthly.merge(monthKey, speed, Double::sum);
                partialSamples.merge(monthKey, 1.0, Double::sum);
            }
        }
        return new ProcessingResult(partialMonthly, partialSamples);
    }

    private BusEvent parseLine(String line, Set<Integer> activeLineIds) {
        try {
            String[] parts = line.split(",");
            if (parts.length <= IDX_BUS_ID) return null;

            int lineId = Integer.parseInt(parts[IDX_LINE_ID]);
            if (!activeLineIds.contains(lineId)) return null;

            int busId = Integer.parseInt(parts[IDX_BUS_ID]);
            int tripId = Integer.parseInt(parts[IDX_TRIP_ID]);
            int stopId = Integer.parseInt(parts[2]);
            double latitude = Integer.parseInt(parts[IDX_LATITUDE]) / 1e7;
            double longitude = Integer.parseInt(parts[IDX_LONGITUDE]) / 1e7;
            
            String dateStr = parts[IDX_DATAGRAM_DATE];
            long timestamp = fastParseDate(dateStr).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            return new BusEvent(busId, lineId, tripId, stopId, latitude, longitude, timestamp);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime fastParseDate(String s) {
        int year   = (s.charAt(0)-'0')*1000 + (s.charAt(1)-'0')*100 + (s.charAt(2)-'0')*10 + (s.charAt(3)-'0');
        int month  = (s.charAt(5)-'0')*10 + (s.charAt(6)-'0');
        int day    = (s.charAt(8)-'0')*10 + (s.charAt(9)-'0');
        int hour   = (s.charAt(11)-'0')*10 + (s.charAt(12)-'0');
        int minute = (s.charAt(14)-'0')*10 + (s.charAt(15)-'0');
        int second = (s.charAt(17)-'0')*10 + (s.charAt(18)-'0');
        return LocalDateTime.of(year, month, day, hour, minute, second);
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
