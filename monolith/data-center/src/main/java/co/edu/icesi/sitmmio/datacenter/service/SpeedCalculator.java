package co.edu.icesi.sitmmio.datacenter.service;

import co.edu.icesi.sitmmio.datacenter.model.BusEvent;

import java.time.Duration;
import java.util.*;

public class SpeedCalculator {

    // Estimación por Ruta por Mes: lineId + "_" + mes + "_" + año
    private final Map<String, double[]> monthlyAccumulated = new HashMap<>();

    // Estimación por Tramos: lineId + "_" + stopId_origen + "_" + stopId_destino
    private final Map<String, double[]> segmentAccumulated = new HashMap<>();

    public void processChunk(List<BusEvent> events) {

        Map<String, List<BusEvent>> trajectories = new HashMap<>();
        for (BusEvent e : events) {
            String key = e.getBusId() + "_" + e.getTripId();
            trajectories.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }

        for (List<BusEvent> trajectory : trajectories.values()) {
            trajectory.sort(Comparator.comparing(BusEvent::getDatagramDate));

            for (int i = 1; i < trajectory.size(); i++) {
                BusEvent prev = trajectory.get(i - 1);
                BusEvent curr = trajectory.get(i);

                double distKm = haversine(
                        prev.getLatitude(), prev.getLongitude(),
                        curr.getLatitude(), curr.getLongitude());

                double hours = Duration.between(
                        prev.getDatagramDate(),
                        curr.getDatagramDate()).toSeconds() / 3600.0;

                if (hours <= 0)           continue;
                if (distKm <= 0)          continue;
                if (distKm > 1.0)         continue;
                if (distKm / hours > 120) continue;

                double speed = distKm / hours;

                // 1. Agrupación Mensual
                String monthlyKey = curr.getLineId() + "_"
                        + curr.getDatagramDate().getMonthValue() + "_"
                        + curr.getDatagramDate().getYear();

                monthlyAccumulated.computeIfAbsent(monthlyKey, k -> new double[]{0.0, 0.0});
                monthlyAccumulated.get(monthlyKey)[0] += speed;
                monthlyAccumulated.get(monthlyKey)[1] += 1;

                // 2. Agrupación por Tramos (solo si hay cambio de parada válida)
                if (prev.getStopId() != -1 && curr.getStopId() != -1 && prev.getStopId() != curr.getStopId()) {
                    String segmentKey = curr.getLineId() + "_"
                            + prev.getStopId() + "_"
                            + curr.getStopId();

                    segmentAccumulated.computeIfAbsent(segmentKey, k -> new double[]{0.0, 0.0});
                    segmentAccumulated.get(segmentKey)[0] += speed;
                    segmentAccumulated.get(segmentKey)[1] += 1;
                }
            }
        }
    }

    public Map<String, double[]> getMonthlyAccumulated() {
        return monthlyAccumulated;
    }

    public Map<String, double[]> getSegmentAccumulated() {
        return segmentAccumulated;
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