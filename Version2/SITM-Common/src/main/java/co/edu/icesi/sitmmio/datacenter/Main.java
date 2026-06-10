package co.edu.icesi.sitmmio.datacenter;

import co.edu.icesi.sitmmio.datacenter.model.BusEvent;
import co.edu.icesi.sitmmio.datacenter.repository.ActiveLinesLoader;
import co.edu.icesi.sitmmio.datacenter.repository.CsvLoader;
import co.edu.icesi.sitmmio.datacenter.model.ProcessingResult;
import co.edu.icesi.sitmmio.datacenter.service.PartialResults;
import co.edu.icesi.sitmmio.datacenter.service.QueryEngine;
import co.edu.icesi.sitmmio.datacenter.service.SpeedCalculator;

import java.util.*;
import java.util.concurrent.*;

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

        System.out.println("Version: 2.3 - Concurrente Dual (ProcessingResult)");
        System.out.println("Threads: " + numThreads);
        System.out.println("Chunk size: " + CHUNK_SIZE);

        long t0 = System.currentTimeMillis();

        ActiveLinesLoader linesLoader = new ActiveLinesLoader();
        Set<Integer> activeLines = linesLoader.loadActiveLineIds(linesPath);

        System.out.println("Leyendo y procesando en paralelo: " + csvPath);
        CsvLoader loader = new CsvLoader(csvPath);
        
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        SpeedCalculator calculator = new SpeedCalculator();
        List<Future<ProcessingResult>> futures = new ArrayList<>();

        long totalLines = 0;
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

        Map<String, double[]> accumulatedMonthly = new HashMap<>();
        Map<String, double[]> accumulatedArcs = new HashMap<>();
        PartialResults partialResults = new PartialResults();

        int completed = 0;
        for (Future<ProcessingResult> future : futures) {
            ProcessingResult result = future.get();
            completed++;
            if (completed % 10 == 0) {
                System.out.print(".");
            }
            partialResults.merge(accumulatedMonthly, result.getMonthlyData());
            partialResults.merge(accumulatedArcs, result.getArcData());
        }
        System.out.println("\nConsolidación completada.");

        pool.shutdown();
        long tEnd = System.currentTimeMillis();

        System.out.println("\nReporte Mensual (QueryEngine):");
        QueryEngine queryEngine = new QueryEngine(accumulatedMonthly);
        Map<String, Double> averages = queryEngine.getAllAverages();
        averages.entrySet().stream().limit(10).forEach(e -> {
            System.out.printf("  %-25s -> %.2f km/h%n", e.getKey(), e.getValue());
        });

        System.out.println("\nReporte por Tramos (Muestra):");
        accumulatedArcs.entrySet().stream().limit(10).forEach(e -> {
            System.out.printf("  %-25s -> %.2f km/h (%d muestras)%n", 
                e.getKey(), e.getValue()[0]/e.getValue()[1], (long)e.getValue()[1]);
        });

        System.out.println("\n----------------------------------------------------------");
        System.out.println("Threads utilizados:      " + numThreads);
        System.out.println("Tiempo total ejecucion:  " + (tEnd - t0) + " ms");
        System.out.println("Lineas con resultado mensual: " + averages.size());
        System.out.println("Lineas con resultado tramos:  " + accumulatedArcs.size());
    }
}