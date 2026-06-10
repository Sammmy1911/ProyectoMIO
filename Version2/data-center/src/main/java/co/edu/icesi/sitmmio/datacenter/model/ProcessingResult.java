package co.edu.icesi.sitmmio.datacenter.model;

import java.util.Map;

/**
 * Contenedor para los resultados parciales de un hilo.
 * Ideal para que el compañero del distribuido lo use como DTO.
 */
public class ProcessingResult {
    public final Map<String, double[]> monthlyAverages;
    public final Map<String, double[]> arcAverages;

    public ProcessingResult(Map<String, double[]> monthly, Map<String, double[]> arcs) {
        this.monthlyAverages = monthly;
        this.arcAverages = arcs;
    }
}
