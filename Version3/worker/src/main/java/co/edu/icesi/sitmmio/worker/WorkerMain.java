package co.edu.icesi.sitmmio.worker;

import co.edu.uicesi.sitmmio.datacenter.*;
import com.zeroc.Ice.*;

import java.io.*;
import java.util.*;

public class WorkerMain implements Worker {

    private final SpeedCalculator calculator = new SpeedCalculator();
    private Set<Integer> activeLines = new HashSet<>();

    @Override
    public void initialize(int[] activeLines, Current current) {
        this.activeLines = new HashSet<>();
        for (int lineId : activeLines) {
            this.activeLines.add(lineId);
        }
        System.out.println("[Worker] " + current.adapter.getName() + " LISTO con " + activeLines.length + " líneas activas.");
    }

    @Override
    public ProcessingResult processChunk(byte[] data, Current current) {
        return calculator.processByteChunk(data, activeLines);
    }

    public static void main(String[] args) {
        InitializationData id = new InitializationData();
        id.properties = Util.createProperties();
        
        File configFile = new File("config.worker");
        if (configFile.exists()) {
            id.properties.load("config.worker");
            System.out.println("[Worker] Cargando configuración desde config.worker");
        }

        id.properties.setProperty("Ice.Override.Compress", "1");
        id.properties.setProperty("Ice.MessageSizeMax", "102400");
        id.properties.setProperty("Ice.Default.Timeout", "60000");
        
        try (Communicator communicator = Util.initialize(id)) {
            // Leer puerto de propiedades o de argumento o default 10001
            String portStr = id.properties.getPropertyWithDefault("Worker.Port", "10001");
            if (args.length > 0) portStr = args[0];
            
            int port = Integer.parseInt(portStr);
            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("WorkerAdapter", "default -p " + port);
            WorkerMain servant = new WorkerMain();
            adapter.add(servant, Util.stringToIdentity("Worker"));
            adapter.activate();
            System.out.println("[Worker] Escuchando en el puerto " + port + " (Compresión Activa)...");
            communicator.waitForShutdown();
        } catch (java.lang.Exception e) {
            e.printStackTrace();
        }
    }
}
