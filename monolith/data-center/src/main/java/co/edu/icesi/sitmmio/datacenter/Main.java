package co.edu.icesi.sitmmio.datacenter;

import co.edu.icesi.sitmmio.datacenter.repository.ActiveLinesLoader;
import co.edu.icesi.sitmmio.datacenter.repository.CsvLoader;
import co.edu.icesi.sitmmio.datacenter.service.SpeedCalculator;
import co.edu.icesi.sitmmio.datacenter.model.BusEvent;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

public class Main {

    private static final int CHUNK_SIZE = 500_000;

    public static void main(String[] args) throws Exception {

        if (args.length < 2) {
            System.out.println("Uso: java -jar data-center.jar <datagramas.csv> <lines-active.csv>");
            System.exit(1);
        }

        String csvPath   = args[0];
        String linesPath = args[1];

        long t0 = System.currentTimeMillis();

        // cargar líneas activas
        ActiveLinesLoader linesLoader = new ActiveLinesLoader();
        Set<Integer> activeLines = linesLoader.loadActiveLineIds(linesPath);

        // procesar por chunks
        System.out.println("Procesando: " + csvPath);
        System.out.println("Chunk size: " + CHUNK_SIZE);

        CsvLoader loader = new CsvLoader(csvPath);
        SpeedCalculator calculator = new SpeedCalculator();

        int chunkNumber = 0;
        long tCalcTotal = 0;

        while (!loader.isFinished()) {
            List<BusEvent> chunk = loader.nextChunk(CHUNK_SIZE, activeLines);
            if (chunk.isEmpty()) break;

            chunkNumber++;
            long tChunk = System.currentTimeMillis();
            calculator.processChunk(chunk);
            tCalcTotal += System.currentTimeMillis() - tChunk;

            System.out.println("Chunk " + chunkNumber
                    + " procesado | eventos acumulados: " + loader.getTotalLoaded());
        }

        loader.close();

        long tEnd = System.currentTimeMillis();

        // Generar reportes CSV
        String inputFileName = new java.io.File(csvPath).getName();
        String monthlyReportName = "monthly_" + inputFileName;
        String segmentReportName = "segments_" + inputFileName;

        saveReport(monthlyReportName, "lineId_mes_año,velocidad_promedio,muestras", calculator.getMonthlyAccumulated());
        saveReport(segmentReportName, "lineId_stopIdOrigen_stopIdDestino,velocidad_promedio,muestras", calculator.getSegmentAccumulated());

        System.out.println("\nReportes generados:");
        System.out.println("  - " + monthlyReportName);
        System.out.println("  - " + segmentReportName);

        System.out.println("\n----------------------------------------------------------");
        System.out.println("Chunks procesados:       " + chunkNumber);
        System.out.println("Eventos validos totales: " + loader.getTotalLoaded());
        System.out.println("Tiempo calculo total:    " + tCalcTotal + " ms");
        System.out.println("Tiempo total ejecucion:  " + (tEnd - t0) + " ms");
        System.out.println("Lineas con resultado mensual: " + calculator.getMonthlyAccumulated().size());
        System.out.println("Tramos con resultado:         " + calculator.getSegmentAccumulated().size());

        long totalTimeMs = tEnd - t0;
        long minutes = (totalTimeMs / 1000) / 60;
        long seconds = (totalTimeMs / 1000) % 60;
        int totalLines = loader.getTotalLoaded();
        double throughput = totalLines / (totalTimeMs / 1000.0);
        int numThreads = 1; // Monolítico actual es single-thread

        System.out.println("\n================ RESUMEN DE RENDIMIENTO ================");
        System.out.printf("Tiempo total:          %02d:%02d (%d ms)%n", minutes, seconds, totalTimeMs);
        System.out.printf("Total líneas:          %,d%n", totalLines);
        System.out.printf("Velocidad:             %,.2f líneas/seg%n", throughput);
        System.out.printf("Threads:               %d%n", numThreads);
        System.out.println("========================================================");
    }

        private static void saveReport(String fileName, String header, Map<String, double[]> data) throws Exception {
        try (PrintWriter pw = new PrintWriter(new FileWriter(fileName))) {
            pw.println(header);
            for (Map.Entry<String, double[]> entry : data.entrySet()) {
                double[] val = entry.getValue();
                if (val[1] > 0) {
                    pw.printf("%s,%.2f,%.0f%n", entry.getKey(), val[0] / val[1], val[1]);
                }
            }
        }
        }
        }
// test change
