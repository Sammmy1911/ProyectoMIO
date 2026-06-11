package co.edu.icesi.sitmmio.visualizer;

import co.edu.uicesi.sitmmio.datacenter.*;
import co.edu.icesi.sitmmio.visualizer.controller.VisualizerController;
import co.edu.icesi.sitmmio.visualizer.model.BusModel;
import co.edu.icesi.sitmmio.visualizer.view.MapVisualizer;
import com.zeroc.Ice.*;
import java.io.*;
import java.util.*;

public class VisualizerMain {

    private static SITMMasterPrx masterProxy;

    public static void main(String[] args) {
        InitializationData id = new InitializationData();
        id.properties = Util.createProperties();
        
        File configFile = new File("config.visualizer");
        if (configFile.exists()) {
            id.properties.load("config.visualizer");
            System.out.println("[Visualizer] Cargando configuración desde config.visualizer");
        }

        id.properties.setProperty("Ice.Override.Compress", "1");
        id.properties.setProperty("Ice.Default.Timeout", "60000");

        try (Communicator c = Util.initialize(id)) {
            String masterHost = id.properties.getPropertyWithDefault("Master.Host", "localhost");
            String masterPort = id.properties.getPropertyWithDefault("Master.Port", "10000");
            String observerPort = id.properties.getPropertyWithDefault("Observer.Port", "10002");
            if (args.length > 0) masterHost = args[0];
            if (args.length > 1) masterPort = args[1];
            if (args.length > 2) observerPort = args[2];

            masterProxy = SITMMasterPrx.checkedCast(c.stringToProxy("DataCenter:default -h " + masterHost + " -p " + masterPort));
            if (masterProxy == null) return;

            BusModel model = new BusModel();
            MapVisualizer view = new MapVisualizer(model);
            VisualizerController controller = new VisualizerController(model, VisualizerMain::showPerformanceBox);

            ObjectAdapter a = c.createObjectAdapterWithEndpoints("ObserverAdapter", "default -p " + observerPort);
            a.add(controller, Util.stringToIdentity("Visualizer"));
            a.activate();

            SITMObserverPrx prx = SITMObserverPrx.uncheckedCast(a.createProxy(Util.stringToIdentity("Visualizer")));
            masterProxy.subscribe(prx);
            
            view.show();
            System.out.println("[Visualizer] Monitor de Rendimiento Activo.");
            c.waitForShutdown();
        } catch (java.lang.Exception e) { e.printStackTrace(); }
    }

    public static void showPerformanceBox(PerformanceMetrics m) {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("   CUADRO DE HONOR: RENDIMIENTO DISTRIBUIDO");
        System.out.println("=".repeat(50));
        System.out.printf("1. Tiempo Ingesta (Bus -> Master):   %.2f seg%n", m.ingestionTimeSec);
        System.out.printf("2. Tiempo Procesamiento (Workers):   %.2f seg%n", m.processingTimeSec);
        System.out.printf("3. TIEMPO TOTAL (Fin a Fin):        %.2f seg (%.2f min)%n", m.totalTimeSec, m.totalTimeSec/60.0);
        System.out.println("-".repeat(50));
        System.out.printf("Datos Procesados:                   %.2f MB%n", m.dataSizeMB);
        System.out.printf("Velocidad Efectiva del Sistema:     %.2f MB/s%n", m.dataSizeMB / m.totalTimeSec);
        System.out.println("=".repeat(50));
        System.out.println("[DataCenter] El reporte oficial ha sido generado en su carpeta local.");
    }
}
