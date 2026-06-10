package co.edu.icesi.sitmmio.datacenter.service;

import co.edu.icesi.sitmmio.datacenter.model.BusEvent;
import co.edu.icesi.sitmmio.datacenter.model.ProcessingResult;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

public class SpeedCalculator {

    private static final int IDX_STOP_ID       = 2;
    private static final int IDX_LATITUDE      = 4;
    private static final int IDX_LONGITUDE     = 5;
    private static final int IDX_LINE_ID       = 7;
    private static final int IDX_TRIP_ID       = 8;
    private static final int IDX_DATAGRAM_DATE = 10;
    private static final int IDX_BUS_ID        = 11;

    private SITMObserver observer;
    private long notifyCounter = 0;

    public void setObserver(SITMObserver observer) {
        this.observer = observer;
    }

    /**
     * Versión 2.2 – Dual (Mensual + Tramos).
     * Recibe líneas crudas, parsea en paralelo y calcula ambos reportes.
     */
    public ProcessingResult processRawChunk(List<String> rawLines, Set<Integer> activeLineIds) {
        List<BusEvent> events = new ArrayList<>(rawLines.size());

        for (String line : rawLines) {
            BusEvent event = parseLine(line, activeLineIds);
            if (event != null) {
                events.add(event);
                if (observer != null && ++notifyCounter % 5000 == 0) {
                    observer.onBusMoved(event.getBusId(), event.getLatitude(), event.getLongitude(), event.getLineId());
                }
            }
        }

        Map<String, List<BusEvent>> trajectories = new HashMap<>();
        for (BusEvent e : events) {
            String key = e.getBusId() + "_" + e.getTripId();
            trajectories.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }

        return processTrajectories(trajectories.values());
    }

    public ProcessingResult processTrajectories(Collection<List<BusEvent>> trajectories) {
        Map<String, double[]> partialMonthly = new HashMap<>();
        Map<String, double[]> partialArcs = new HashMap<>();

        for (List<BusEvent> trajectory : trajectories) {
            trajectory.sort(Comparator.comparing(BusEvent::getDatagramDate));

            for (int i = 1; i < trajectory.size(); i++) {
                BusEvent prev = trajectory.get(i - 1);
                BusEvent curr = trajectory.get(i);

                double distKm = haversine(prev.getLatitude(), prev.getLongitude(), curr.getLatitude(), curr.getLongitude());
                double hours = Duration.between(prev.getDatagramDate(), curr.getDatagramDate()).toSeconds() / 3600.0;

                if (hours <= 0 || distKm <= 0 || distKm > 1.0 || (distKm / hours) > 120) continue;

                double speed = distKm / hours;

                // 1. Acumular para reporte MENSUAL
                String monthKey = curr.getLineId() + "_" + curr.getDatagramDate().getMonthValue() + "_" + curr.getDatagramDate().getYear();
                accumulate(partialMonthly, monthKey, speed);

                // 2. Acumular para reporte de TRAMOS (Arcos)
                if (prev.getStopId() != curr.getStopId()) {
                    String arcKey = curr.getLineId() + "_" + prev.getStopId() + "_" + curr.getStopId();
                    accumulate(partialArcs, arcKey, speed);
                }
            }
        }
        return new ProcessingResult(partialMonthly, partialArcs);
    }

    private void accumulate(Map<String, double[]> map, String key, double speed) {
        map.computeIfAbsent(key, k -> new double[]{0.0, 0.0});
        map.get(key)[0] += speed;
        map.get(key)[1] += 1;
    }

    public BusEvent parseLine(String line, Set<Integer> activeLineIds) {
        try {
            int len = line.length();
            int stopId = 0, rawLat = 0, rawLon = 0, lineId = 0, tripId = 0, busId = 0;
            String dateStr = null;

            int column = 0;
            int start = 0;
            for (int i = 0; i <= len; i++) {
                if (i == len || line.charAt(i) == ',') {
                    if (column == IDX_LINE_ID) {
                        lineId = fastInt(line, start, i);
                        if (lineId == -1 || !activeLineIds.contains(lineId)) return null;
                    } else if (column == IDX_LATITUDE) {
                        rawLat = fastInt(line, start, i);
                        if (rawLat == -1) return null;
                    } else if (column == IDX_LONGITUDE) {
                        rawLon = fastInt(line, start, i);
                        if (rawLon == -1) return null;
                    } else if (column == IDX_BUS_ID) {
                        busId = fastInt(line, start, i);
                    } else if (column == IDX_TRIP_ID) {
                        tripId = fastInt(line, start, i);
                    } else if (column == IDX_STOP_ID) {
                        stopId = fastInt(line, start, i);
                    } else if (column == IDX_DATAGRAM_DATE) {
                        dateStr = line.substring(start, i);
                    }
                    start = i + 1;
                    column++;
                }
            }
            double latitude  = rawLat / 1e7;
            double longitude = rawLon / 1e7;
            LocalDateTime date = fastParseDate(dateStr);
            return new BusEvent(busId, lineId, tripId, stopId, latitude, longitude, date);
        } catch (Exception e) {
            return null;
        }
    }

    private int fastInt(String s, int start, int end) {
        int res = 0;
        int i = start;
        boolean neg = false;
        if (i < end && s.charAt(i) == '-') { neg = true; i++; }
        if (i >= end) return -1;
        for (; i < end; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') break;
            res = res * 10 + (c - '0');
        }
        return neg ? -res : res;
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
