package co.edu.icesi.sitmmio.datacenter.service;

import datacenter.TramoInfo;
import java.util.Map;

public class PartialResults {

    public void merge(Map<String, double[]> accumulated, String key, double totalSpeed, double samples) {
        double[] data = accumulated.computeIfAbsent(key, k -> new double[]{0.0, 0.0});
        data[0] += totalSpeed;
        data[1] += samples;
    }

    public void merge(Map<String, double[]> accumulated, Map<String, double[]> partial) {
        for (Map.Entry<String, double[]> entry : partial.entrySet()) {
            merge(accumulated, entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
        }
    }

    public void mergeIce(Map<String, double[]> accumulated, Map<String, TramoInfo> partial) {
        for (Map.Entry<String, TramoInfo> entry : partial.entrySet()) {
            merge(accumulated, entry.getKey(), entry.getValue().sumaVelocidad, entry.getValue().conteo);
        }
    }

    public Map<String, TramoInfo> toIceMap(Map<String, double[]> partial) {
        java.util.Map<String, TramoInfo> result = new java.util.HashMap<>();
        for (Map.Entry<String, double[]> entry : partial.entrySet()) {
            result.put(entry.getKey(), new TramoInfo(entry.getValue()[0], (long) entry.getValue()[1]));
        }
        return result;
    }
}
