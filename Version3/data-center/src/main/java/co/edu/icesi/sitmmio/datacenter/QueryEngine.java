package co.edu.icesi.sitmmio.datacenter;

import co.edu.uicesi.sitmmio.datacenter.ProcessingResult;
import java.util.*;

public class QueryEngine {

    private final ProcessingResult results;

    public QueryEngine(ProcessingResult results) {
        this.results = results;
    }

    public OptionalDouble getSpeedReport(int lineId, int month, int year) {
        String key = lineId + "_" + month + "_" + year;
        Double accumulated = results.monthlyAverages.get(key);
        Double count = results.samples.get(key);
        
        if (accumulated == null || count == null || count == 0) return OptionalDouble.empty();
        return OptionalDouble.of(accumulated / count);
    }

    public Map<String, Double> getAllAverages() {
        Map<String, Double> finalAverages = new TreeMap<>();
        results.monthlyAverages.forEach((key, value) -> {
            Double count = results.samples.get(key);
            if (count != null && count > 0) {
                finalAverages.put(key, value / count);
            }
        });
        return finalAverages;
    }
}