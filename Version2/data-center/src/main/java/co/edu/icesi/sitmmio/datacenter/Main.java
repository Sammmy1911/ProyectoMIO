package co.edu.icesi.sitmmio.datacenter;

import co.edu.icesi.sitmmio.datacenter.model.BusEvent;
import co.edu.icesi.sitmmio.datacenter.repository.ActiveLinesLoader;
import co.edu.icesi.sitmmio.datacenter.repository.CsvLoader;
import co.edu.icesi.sitmmio.datacenter.service.QueryEngine;
import co.edu.icesi.sitmmio.datacenter.service.SpeedCalculator;

import java.util.*;
import java.util.concurrent.*;

public class Main {

    private static final int CHUNK_SIZE = 100_000; // Reducido para mejor feedback y menos RAM

    public static void main(String[] args) throws Exception {

        if (args.length < 2) {
            System.out.println("Uso: java -jar data-center.jar <datagramas.csv> <lines-active.csv> [numThreads]");
            System.exit(1);
        }

        String csvPath    = args[0];
        String linesPath  = args[1];
        int    numThreads = args.length >= 3 ? Integer.parseInt(args[2]) : Runtime.getRuntime().availableProcessors();

        System.out.println("Version: 2 - Concurrente (Optimizada)");
        System.out.println("Threads: " + numThreads);
        System.out.println("Chunk size: " + CHUNK_SIZE);

        long t0 = System.currentTimeMillis();

        // ── cargar líneas activas ─────────────────────────────────────────────
        ActiveLinesLoader linesLoader = new ActiveLinesLoader();
        Set<Integer> activeLines = linesLoader.loadActiveLineIds(linesPath);

        System.out.println("Leyendo y procesando en paralelo: " + csvPath);
        CsvLoader loader = new CsvLoader(csvPath);
        
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        SpeedCalculator calculator = new SpeedCalculator();
        List<Future<Map<String, double[]>>> futures = new ArrayList<>();

        long totalLines = 0;
        // FLUJO OPTIMIZADO: Mientras el Main lee, los hilos ya están calculando
        while (!loader.isFinished()) {
            List<String> rawChunk = loader.nextRawChunk(CHUNK_SIZE);
            if (!rawChunk.isEmpty()) {
                totalLines += rawChunk.size();
                if (totalLines % 1_000_000 == 0) {
                    System.out.println("-> Líneas leídas: " + (totalLines / 1_000_000) + " Millones...");
                }
                futures.add(pool.submit(() -> calculator.processRawChunk(rawChunk, activeLines)));
            }
        }
        loader.close();
        System.out.println("Total líneas leídas: " + totalLines);
        System.out.println("Lectura finalizada. Consolidando resultados finales (Reduce)...");

        // ── Reduce: Master consolida los resultados parciales ─────────────────
        Map<String, double[]> accumulated = new HashMap<>();

        int completed = 0;
        for (Future<Map<String, double[]>> future : futures) {
            Map<String, double[]> partial = future.get();
            completed++;
            if (completed % 10 == 0) {
                System.out.print("."); // Feedback durante la consolidación
            }
            for (Map.Entry<String, double[]> entry : partial.entrySet()) {
                accumulated.computeIfAbsent(entry.getKey(), k -> new double[]{0.0, 0.0});
                accumulated.get(entry.getKey())[0] += entry.getValue()[0];
                accumulated.get(entry.getKey())[1] += entry.getValue()[1];
            }
        }
        System.out.println("\nConsolidación completada.");

        pool.shutdown();
        long tEnd = System.currentTimeMillis();

        // ── resultados ────────────────────────────────────────────────────────
        QueryEngine queryEngine = new QueryEngine(accumulated);
        Map<String, Double> averages = queryEngine.getAllAverages();

        System.out.println("\nResultados (lineId_mes_año -> velocidad promedio km/h):");
        System.out.println("----------------------------------------------------------");
        for (Map.Entry<String, Double> entry : averages.entrySet()) {
            String[] parts = entry.getKey().split("_");
            System.out.printf("  %-25s -> %.2f km/h  (%d muestras)%n",
                    entry.getKey(),
                    entry.getValue(),
                    queryEngine.getSampleCount(
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2])));
        }

        System.out.println("\n----------------------------------------------------------");
        System.out.println("Threads utilizados:      " + numThreads);
        System.out.println("Tiempo total ejecucion:  " + (tEnd - t0) + " ms");
        System.out.println("Lineas con resultado:    " + averages.size());
    }
}