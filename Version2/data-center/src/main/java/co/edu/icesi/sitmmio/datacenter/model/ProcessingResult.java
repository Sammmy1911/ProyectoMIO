package co.edu.icesi.sitmmio.datacenter.model;

import java.util.Map;

/**
 * Contenedor para los resultados parciales de un hilo.
 * Ideal para que el compañero del distribuido lo use como DTO.
 */
public class ProcessingResult {
    public final Map<String, double[]> averages;

    public ProcessingResult(Map<String, double[]> averages) {
        this.averages = averages;
    }
}
