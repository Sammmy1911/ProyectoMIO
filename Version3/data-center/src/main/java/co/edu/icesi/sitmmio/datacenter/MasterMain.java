package co.edu.icesi.sitmmio.datacenter;

import co.edu.uicesi.sitmmio.datacenter.*;
import com.zeroc.Ice.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class MasterMain implements SITMMaster {

    private final List<WorkerPrx> workers = new ArrayList<>();
    private final List<SITMObserverPrx> observers = new ArrayList<>();
    
    private final Map<String, Double> monthlyAccumulated = new ConcurrentHashMap<>();
    private final Map<String, Double> sampleCount = new ConcurrentHashMap<>();
    
    private final AtomicLong totalBytesProcessed = new AtomicLong(0);
    private int nextWorker = 0;
    private final Semaphore workerSemaphore = new Semaphore(100); 
    
    private long ingestionEndTime = 0;

    public void addWorker(WorkerPrx worker) {
        workers.add(worker);
    }

    @Override
    public void initialize(int[] activeLines, Current current) {
        System.out.println("[Master] Propagando inicialización a " + workers.size() + " workers en paralelo...");
        for (WorkerPrx worker : workers) {
            worker.initializeAsync(activeLines).whenComplete((v, ex) -> {
                if (ex != null) System.err.println("[Master] Error inicializando un worker.");
            });
        }
    }

    @Override
    public void processEvents(byte[] data, Current current) {
        if (workers.isEmpty()) return;
        WorkerPrx worker;
        synchronized (this) {
            worker = workers.get(nextWorker);
            nextWorker = (nextWorker + 1) % workers.size();
        }

        try {
            workerSemaphore.acquire();
            worker.processChunkAsync(data).whenComplete((result, ex) -> {
                workerSemaphore.release();
                if (ex == null) {
                    mergeResults(result);
                    totalBytesProcessed.addAndGet(data.length);
                }
            });
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        
        notifyObservers(data);
    }

    @Override
    public void finishIngestion(long startTimeMillis, Current current) {
        this.ingestionEndTime = System.currentTimeMillis();
        System.out.println("[Master] Ingesta completada. Sincronizando Workers...");
        
        try {
            workerSemaphore.acquire(100);
            long processingEndTime = System.currentTimeMillis();
            
            saveReportToCsv("reporte_final_datacenter.csv");

            double ingestionSec = (ingestionEndTime - startTimeMillis) / 1000.0;
            double processingSec = (processingEndTime - ingestionEndTime) / 1000.0;
            double totalSec = (processingEndTime - startTimeMillis) / 1000.0;
            double sizeMB = totalBytesProcessed.get() / (1024 * 1024.0);

            PerformanceMetrics metrics = new PerformanceMetrics(ingestionSec, processingSec, totalSec, sizeMB);

            for (SITMObserverPrx obs : observers) {
                obs.onProcessingCompleteAsync(metrics);
            }
            
            workerSemaphore.release(100);
            System.out.println("[Master] Ciclo completo. Reporte generado.");
        } catch (java.lang.Exception e) { e.printStackTrace(); }
    }

    private void saveReportToCsv(String fileName) {
        try (PrintWriter pw = new PrintWriter(new File(fileName))) {
            pw.println("Ruta_Mes_Año,Velocidad_Promedio,Muestras");
            TreeMap<String, Double> sorted = new TreeMap<>(monthlyAccumulated);
            for (Map.Entry<String, Double> entry : sorted.entrySet()) {
                double count = sampleCount.getOrDefault(entry.getKey(), 0.0);
                if (count > 0) pw.printf("%s,%.2f,%.0f%n", entry.getKey(), entry.getValue() / count, count);
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void mergeResults(ProcessingResult result) {
        result.monthlyAverages.forEach((key, value) -> monthlyAccumulated.merge(key, value, Double::sum));
        result.samples.forEach((key, value) -> sampleCount.merge(key, value, Double::sum));
    }

    private void notifyObservers(byte[] data) {
        if (observers.isEmpty()) return;
        try {
            String content = new String(data);
            String[] lines = content.split("\n");
            for (int i = 0; i < lines.length; i += 2000) {
                String[] p = lines[i].split(",");
                if (p.length > 11) {
                    for (SITMObserverPrx obs : observers) 
                        obs.onBusMovedAsync(Integer.parseInt(p[11]), Integer.parseInt(p[4])/1e7, Integer.parseInt(p[5])/1e7, Integer.parseInt(p[7]), System.currentTimeMillis());
                }
            }
        } catch (java.lang.Exception ignored) {}
    }

    @Override public void subscribe(SITMObserverPrx o, Current c) { observers.add(o); }
    @Override public ProcessingResult getMonthlyReport(Current c) { return new ProcessingResult(new HashMap<>(monthlyAccumulated), new HashMap<>(sampleCount)); }

    public static void main(String[] args) {
        InitializationData id = new InitializationData();
        id.properties = Util.createProperties();
        
        // Intentar cargar archivo de configuración si existe
        File configFile = new File("config.master");
        if (configFile.exists()) {
            id.properties.load("config.master");
            System.out.println("[Master] Cargando configuración desde config.master");
        }

        id.properties.setProperty("Ice.Override.Compress", "1");
        id.properties.setProperty("Ice.MessageSizeMax", "102400");
        id.properties.setProperty("Ice.Default.Timeout", "60000");

        try (Communicator c = Util.initialize(id)) {
            ObjectAdapter a = c.createObjectAdapterWithEndpoints("MasterAdapter", "default -p 10000");
            MasterMain m = new MasterMain();
            
            // 1. Cargar desde propiedades (archivo)
            Map<String, String> props = id.properties.getPropertiesForPrefix("worker.");
            for (String val : props.values()) {
                addWorkerToMaster(c, m, val);
            }

            // 2. Cargar desde argumentos (consola) como adicional
            if (args.length > 0) {
                for (String endpoint : args) {
                    addWorkerToMaster(c, m, endpoint);
                }
            }
            
            // 3. Default si no hay nada
            if (m.workers.isEmpty()) {
                addWorkerToMaster(c, m, "127.0.0.1:10001");
            }

            a.add(m, Util.stringToIdentity("DataCenter"));
            a.activate();
            System.out.println("[Master] Listo. Workers conectados: " + m.workers.size());
            c.waitForShutdown();
        } catch (java.lang.Exception e) { e.printStackTrace(); }
    }

    private static void addWorkerToMaster(Communicator c, MasterMain m, String endpoint) {
        try {
            String proxyStr = endpoint.contains(":") 
                ? "Worker:default -h " + endpoint.split(":")[0] + " -p " + endpoint.split(":")[1]
                : "Worker:default -p " + endpoint;
            
            WorkerPrx w = WorkerPrx.checkedCast(c.stringToProxy(proxyStr));
            if (w != null) {
                m.addWorker(w);
                System.out.println("[Master] Worker conectado -> " + proxyStr);
            }
        } catch (java.lang.Exception e) {
            System.err.println("[Master] No se pudo conectar a " + endpoint);
        }
    }
}
