package co.edu.icesi.sitmmio.simulator;

import co.edu.uicesi.sitmmio.datacenter.*;
import com.zeroc.Ice.*;
import java.io.*;
import java.util.*;

public class SimulatorMain {

    private static final int CHUNK_SIZE_BYTES = 4 * 1024 * 1024;

    public static void main(String[] args) {
        InitializationData id = new InitializationData();
        id.properties = Util.createProperties();
        
        File configFile = new File("config.simulator");
        if (configFile.exists()) {
            id.properties.load("config.simulator");
            System.out.println("[Simulator] Cargando configuración desde config.simulator");
        }

        id.properties.setProperty("Ice.Override.Compress", "1");
        id.properties.setProperty("Ice.MessageSizeMax", "102400");
        id.properties.setProperty("Ice.Default.Timeout", "60000");

        try (Communicator communicator = Util.initialize(id)) {
            String masterHost = id.properties.getPropertyWithDefault("Master.Host", "localhost");
            String csvPath = id.properties.getPropertyWithDefault("Csv.Path", "datagrams.csv");
            String linesPath = id.properties.getPropertyWithDefault("Lines.Path", "lines-active.csv");

            if (args.length >= 1) csvPath = args[0];
            if (args.length >= 2) linesPath = args[1];
            if (args.length >= 3) masterHost = args[2];

            SITMMasterPrx master = SITMMasterPrx.checkedCast(
                communicator.stringToProxy("DataCenter:default -h " + masterHost + " -p 10000")
            );

            if (master == null) {
                System.err.println("No se pudo conectar al Master en " + masterHost);
                return;
            }

            ActiveLinesLoader linesLoader = new ActiveLinesLoader();
            Set<Integer> activeLines = linesLoader.loadActiveLineIds(linesPath);
            
            System.out.println("[Simulator] Configurando Workers...");
            master.initialize(activeLines.stream().mapToInt(i -> i).toArray());

            System.out.println("[Simulator] Abriendo archivo: " + csvPath);
            CsvLoader loader = new CsvLoader(csvPath);
            long totalBytesSent = 0;
            long tStartGlobal = System.currentTimeMillis();

            java.util.concurrent.Semaphore netSemaphore = new java.util.concurrent.Semaphore(50);
            System.out.println("[Simulator] ¡ARRANCA LA INGESTA!");

            while (!loader.isFinished()) {
                byte[] chunk = loader.nextByteChunk(CHUNK_SIZE_BYTES);
                if (chunk.length > 0) {
                    netSemaphore.acquire();
                    master.processEventsAsync(chunk).whenComplete((v, ex) -> netSemaphore.release());
                    totalBytesSent += chunk.length;
                    if (totalBytesSent % (100 * 1024 * 1024) == 0) {
                        System.out.println("[Simulator] Enviados: " + (totalBytesSent / (1024 * 1024)) + " MB");
                    }
                }
            }
            loader.close();
            netSemaphore.acquire(50);
            
            master.finishIngestion(tStartGlobal);
            System.out.println("[Simulator] Ingesta finalizada. Total: " + (totalBytesSent / (1024 * 1024)) + " MB");

        } catch (java.lang.Exception e) {
            e.printStackTrace();
        }
    }
}
