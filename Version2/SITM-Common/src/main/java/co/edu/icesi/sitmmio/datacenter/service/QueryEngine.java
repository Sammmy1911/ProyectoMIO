package co.edu.icesi.sitmmio.datacenter.service;

import java.util.*;

public class QueryEngine {

    private final Map<String, double[]> accumulated;

    public QueryEngine(Map<String, double[]> accumulated) {
        this.accumulated = accumulated;
    }

    public OptionalDouble getSpeedReport(int lineId, int month, int year) {
        String key = lineId + "_" + month + "_" + year;
        double[] data = accumulated.get(key);
        if (data == null || data[1] == 0) return OptionalDouble.empty();
        return OptionalDouble.of(data[0] / data[1]);
    }

    public Map<String, Double> getAllAverages() {
        Map<String, Double> result = new TreeMap<>();
        for (Map.Entry<String, double[]> entry : accumulated.entrySet()) {
            if (entry.getValue()[1] > 0) {
                result.put(entry.getKey(), entry.getValue()[0] / entry.getValue()[1]);
            }
        }
        return result;
    }

    public long getSampleCount(int lineId, int month, int year) {
        String key = lineId + "_" + month + "_" + year;
        double[] data = accumulated.get(key);
        return data == null ? 0 : (long) data[1];
    }
}