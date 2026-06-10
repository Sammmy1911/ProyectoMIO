package co.edu.icesi.sitmmio.datacenter.service;

/**
 * Interfaz del Observador (Pattern: Observer).
 * Este contrato permite que el Visualizador sea notificado de cambios.
 * En la versión distribuida, esta interfaz será reemplazada por una interfaz Slice de Ice.
 */
public interface SITMObserver {
    
    /**
     * Notifica el movimiento de un bus.
     * @param timestamp Tiempo del evento en milisegundos (epoch).
     */
    void onBusMoved(int busId, double lat, double lon, int lineId, long timestamp);

    /**
     * Notifica una velocidad calculada para una línea/mes/año.
     */
    void onSpeedUpdated(String key, double speed);
}
