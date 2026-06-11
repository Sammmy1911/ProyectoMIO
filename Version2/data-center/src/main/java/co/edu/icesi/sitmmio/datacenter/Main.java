package co.edu.icesi.sitmmio.datacenter;

import co.edu.icesi.sitmmio.datacenter.model.BusEvent;
import co.edu.icesi.sitmmio.datacenter.model.ProcessingResult;
import co.edu.icesi.sitmmio.datacenter.repository.ActiveLinesLoader;
import co.edu.icesi.sitmmio.datacenter.repository.CsvLoader;
import co.edu.icesi.sitmmio.datacenter.service.QueryEngine;
import co.edu.icesi.sitmmio.datacenter.service.SpeedCalculator;
import co.edu.icesi.sitmmio.datacenter.service.MapVisualizer;

import java.util.*;
import java.util.concurrent.*;
import java.io.*;

public class Main {

    private static final int CHUNK_SIZE = 100_000;

    public static void main(String[] args) throws Exception {

        if (args.length < 2) {
            System.out.println("Uso: java -jar data-center.jar <datagramas.csv> <lines-active.csv> [numThreads]");
            System.exit(1);
        }

        String csvPath    = args[0];
        String linesPath  = args[1];
        int    numThreads = args.length >= 3 ? Integer.parseInt(args[2]) : Runtime.getRuntime().availableProcessors();

        System.out.println("Version: 2.2 - Concurrente Dual (Mensual + Tramos)");
        System.out.println("Threads: " + numThreads);
        System.out.println("Chunk size: " + CHUNK_SIZE);

        // ── Bonus: Visualizador de Cali ──────────────────────────────────────
        MapVisualizer visualizer = new MapVisualizer();
        visualizer.showMap();

        long t0 = System.currentTimeMillis();

        // ── cargar líneas activas ─────────────────────────────────────────────
        ActiveLinesLoader linesLoader = new ActiveLinesLoader();
        Set<Integer> activeLines = linesLoader.loadActiveLineIds(linesPath);

        System.out.println("Leyendo y procesando en paralelo: " + csvPath);
        CsvLoader loader = new CsvLoader(csvPath);
        
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        SpeedCalculator calculator = new SpeedCalculator();
        calculator.setObserver(visualizer);

        List<Future<ProcessingResult>> futures = new ArrayList<>();

        // ── Reduce: Consolidar mapa único ─────────────────────────────────────
        Map<String, double[]> accumulated = new HashMap<>();

        long totalLines = 0;
        int activeTasks = 0;

        while (!loader.isFinished() || !futures.isEmpty()) {
            // 1. Llenar el pool con tareas si todavía hay archivo
            if (!loader.isFinished()) {
                List<String> rawChunk = loader.nextRawChunk(CHUNK_SIZE);
                if (!rawChunk.isEmpty()) {
                    totalLines += rawChunk.size();
                    futures.add(pool.submit(() -> calculator.processRawChunk(rawChunk, activeLines)));
                    if (totalLines % 10_000_000 == 0) {
                        System.out.println("-> Progreso: " + (totalLines / 1_000_000) + "M líneas...");
                    }
                }
            }

            // 2. Consolidar tareas que ya terminaron (Streaming Reduce)
            Iterator<Future<ProcessingResult>> it = futures.iterator();
            while (it.hasNext()) {
                Future<ProcessingResult> f = it.next();
                if (f.isDone()) {
                    try {
                        ProcessingResult res = f.get();
                        mergeMaps(accumulated, res.averages);
                        it.remove(); 
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
            
            if (loader.isFinished() && !futures.isEmpty()) {
                Thread.sleep(10);
            }
        }
        loader.close();
        System.out.println("Total líneas procesadas: " + totalLines);
        pool.shutdown();
        long tEnd = System.currentTimeMillis();
        long totalTimeMs = tEnd - t0;
        double totalTimeSec = totalTimeMs / 1000.0;
        
        // ── Generar Reporte Unificado (Ruta-Mes-Año) ─────────────────────────
        String inputBase = new java.io.File(csvPath).getName().replace(".csv", "");
        String reportName = "reporte_" + inputBase + "_final.csv";
        
        try (PrintWriter pw = new PrintWriter(new File(reportName))) {
            pw.println("lineId_mes_año,velocidad_kmh,muestras");
            for (Map.Entry<String, double[]> entry : accumulated.entrySet()) {
                double avg = entry.getValue()[0] / entry.getValue()[1];
                pw.printf("%s,%.2f,%.0f%n", entry.getKey(), avg, entry.getValue()[1]);
            }
        }

        System.out.println("\n[OK] Reporte final (Ruta-Mes) guardado en: " + reportName);

        // ── Resumen de Rendimiento ────────────────────────────────────────────
        long minutes = (totalTimeMs / 1000) / 60;
        long seconds = (totalTimeMs / 1000) % 60;
        double throughput = totalLines / (totalTimeSec > 0 ? totalTimeSec : 1);

        System.out.println("\n================ RESUMEN DE RENDIMIENTO ================");
        System.out.printf("Tiempo total:          %02d:%02d (%d ms)%n", minutes, seconds, totalTimeMs);
        System.out.printf("Total líneas:          %,d%n", totalLines);
        System.out.printf("Velocidad:             %,.2f líneas/seg%n", throughput);
        System.out.printf("Threads:               %d%n", numThreads);
        System.out.println("========================================================");
    }

    private static void mergeMaps(Map<String, double[]> main, Map<String, double[]> partial) {
        for (Map.Entry<String, double[]> entry : partial.entrySet()) {
            main.computeIfAbsent(entry.getKey(), k -> new double[]{0.0, 0.0});
            main.get(entry.getKey())[0] += entry.getValue()[0];
            main.get(entry.getKey())[1] += entry.getValue()[1];
        }
    }

    private static void saveReport(String filename, String header, Map<String, double[]> data) throws IOException {
        try (PrintWriter pw = new PrintWriter(new File(filename))) {
            pw.println(header);
            for (Map.Entry<String, double[]> entry : data.entrySet()) {
                double avg = entry.getValue()[0] / entry.getValue()[1];
                pw.printf("%s,%.2f,%.0f%n", entry.getKey(), avg, entry.getValue()[1]);
            }
        }
    }
}
