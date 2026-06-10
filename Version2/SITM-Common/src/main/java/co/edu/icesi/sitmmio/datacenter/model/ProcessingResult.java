package co.edu.icesi.sitmmio.datacenter.model;

import java.util.Map;

public class ProcessingResult {
    private final Map<String, double[]> monthlyData;
    private final Map<String, double[]> arcData;

    public ProcessingResult(Map<String, double[]> monthlyData, Map<String, double[]> arcData) {
        this.monthlyData = monthlyData;
        this.arcData = arcData;
    }

    public Map<String, double[]> getMonthlyData() {
        return monthlyData;
    }

    public Map<String, double[]> getArcData() {
        return arcData;
    }
}
