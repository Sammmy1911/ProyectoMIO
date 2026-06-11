package co.edu.icesi.sitmmio.visualizer.controller;

import co.edu.uicesi.sitmmio.datacenter.*;
import co.edu.icesi.sitmmio.visualizer.model.BusModel;
import com.zeroc.Ice.Current;
import java.util.function.Consumer;

public class VisualizerController implements SITMObserver {

    private final BusModel model;
    private final Consumer<PerformanceMetrics> onComplete;

    public VisualizerController(BusModel model, Consumer<PerformanceMetrics> onComplete) {
        this.model = model;
        this.onComplete = onComplete;
    }

    @Override
    public void onBusMoved(int busId, double lat, double lon, int lineId, long timestamp, Current current) {
        model.updateBus(busId, lat, lon, lineId, timestamp);
    }

    @Override
    public void onProcessingComplete(PerformanceMetrics metrics, Current current) {
        if (onComplete != null) {
            onComplete.accept(metrics);
        }
    }
}
