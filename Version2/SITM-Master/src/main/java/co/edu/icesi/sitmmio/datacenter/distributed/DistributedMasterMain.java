package co.edu.icesi.sitmmio.datacenter.distributed;

import co.edu.icesi.sitmmio.datacenter.repository.ActiveLinesLoader;
import co.edu.icesi.sitmmio.datacenter.repository.CsvLoader;
import co.edu.icesi.sitmmio.datacenter.service.DatagramPartitioner;
import co.edu.icesi.sitmmio.datacenter.service.PartialResults;
import co.edu.icesi.sitmmio.datacenter.service.QueryEngine;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.Util;
import datacenter.FinalResponse;
import datacenter.RawTask;
import datacenter.TramoInfo;
import datacenter.WorkerServicePrx;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DistributedMasterMain {

    private static final int READ_CHUNK_SIZE = 100_000;
    private static final int REMOTE_TASK_SIZE = 10_000;
    private static final int MAX_PENDING_TASKS_PER_WORKER = 2;

    public static class JobResults {
        public Map<String, double[]> monthly = new HashMap<>();
        public Map<String, double[]> arcs = new HashMap<>();
    }

    public static void main(String[] args) throws Exception {
        com.zeroc.Ice.InitializationData initData = new com.zeroc.Ice.InitializationData();
        initData.properties = Util.createProperties();
        initData.properties.setProperty("Ice.MessageSizeMax", "20480");

        try (Communicator communicator = Util.initialize(args, initData)) {
            // Filtrar argumentos para ignorar los que son de Ice
            List<String> appArgs = new ArrayList<>();
            for (String arg : args) {
                if (!arg.startsWith("--Ice")) appArgs.add(arg);
            }

            if (appArgs.size() < 2) {
                System.out.println("Uso: java ... DistributedMasterMain <datagramas.csv> <lines-active.csv> [workerProxy1...]");
                System.exit(1);
            }

            String csvPath = appArgs.get(0);
            String linesPath = appArgs.get(1);
            // Obtener proxies: de los argumentos o dinámicamente del archivo .cfg
            List<String> workerProxies = new ArrayList<>();
            if (appArgs.size() > 2) {
                workerProxies.addAll(appArgs.subList(2, appArgs.size()));
            } else {
                // Buscar todas las propiedades que empiecen con "Worker.Proxy."
                Map<String, String> props = communicator.getProperties().getPropertiesForPrefix("Worker.Proxy.");
                for (String proxyValue : props.values()) {
                    if (!proxyValue.isEmpty()) {
                        workerProxies.add(proxyValue);
                    }
                }
            }


            if (workerProxies.isEmpty()) {
                System.err.println("Error: No se definieron proxies de Workers (ni en args ni en .cfg)");
                System.exit(1);
            }

            String jobId = "job-" + UUID.randomUUID();
            long t0 = System.currentTimeMillis();

            List<WorkerServicePrx> workers = connectWorkers(communicator, workerProxies);
            System.out.println("Version: 3.3 - Distribuida ICE (.cfg)");
            System.out.println("Workers conectados: " + workers.size());
            System.out.println("Job: " + jobId);

            ActiveLinesLoader linesLoader = new ActiveLinesLoader();
            Set<Integer> activeLines = linesLoader.loadActiveLineIds(linesPath);
            int[] activeLineIds = activeLines.stream().mapToInt(Integer::intValue).toArray();

            for (WorkerServicePrx worker : workers) {
                worker.beginJob(jobId, activeLineIds);
            }

            JobResults results = runDistributedJob(csvPath, jobId, workers);

            // Generar nombre de archivo basado en la entrada
            String inputFileName = new java.io.File(csvPath).getName();
            String outputFileName = "reporte_" + inputFileName;
            
            saveCombinedReport(outputFileName, results);

            System.out.println("\nConsolidación completada.");
            System.out.println("\n[OK] Reporte guardado en: " + outputFileName);

            System.out.println("\n" + "=".repeat(66));
            System.out.println("                 REPORTE DE VELOCIDADES SITM MIO");
            System.out.println("=".repeat(66));
            
            // (Rest of the print statistics logic remains the same)
            // Estadísticas Mensuales (Muestra de los primeros 5)
            System.out.println("\n>>> RESUMEN MENSUAL (Top 5 Líneas):");
            results.monthly.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue()[0] / e.getValue()[1]))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .forEach(e -> System.out.printf("  [Línea/Mes] %-20s | Promedio: %6.2f km/h%n", e.getKey(), e.getValue()));

            // Estadísticas por Tramos (Top 5 más rápidos y 5 más lentos)
            System.out.println("\n>>> ANÁLISIS POR TRAMOS (Arcos):");
            List<Map.Entry<String, Double>> arcSpeeds = results.arcs.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue()[0] / e.getValue()[1]))
                .sorted(Map.Entry.comparingByValue())
                .collect(java.util.stream.Collectors.toList());

            if (!arcSpeeds.isEmpty()) {
                System.out.println("  * Tramos más LENTOS (Congestión):");
                arcSpeeds.stream().limit(5).forEach(e -> 
                    System.out.printf("    %-25s -> %6.2f km/h%n", e.getKey(), e.getValue()));

                System.out.println("\n  * Tramos más RÁPIDOS (Fluidez):");
                for (int i = Math.max(0, arcSpeeds.size() - 5); i < arcSpeeds.size(); i++) {
                    var e = arcSpeeds.get(i);
                    System.out.printf("    %-25s -> %6.2f km/h%n", e.getKey(), e.getValue());
                }
            }

            double globalAvg = results.arcs.values().stream().mapToDouble(v -> v[0]).sum() / 
                               results.arcs.values().stream().mapToLong(v -> (long)v[1]).sum();

            long tEnd = System.currentTimeMillis();
            System.out.println("\n" + "-".repeat(66));
            System.out.println(" ESTADÍSTICAS GLOBALES:");
            System.out.printf("  Total Líneas Procesadas:   %,d%n", results.monthly.size());
            System.out.printf("  Total Tramos Identificados: %,d%n", results.arcs.size());
            System.out.printf("  Velocidad Media Global:    %.2f km/h%n", globalAvg);
            System.out.printf("  Tiempo de Ejecución:       %,d ms%n", (tEnd - t0));
            System.out.println("-".repeat(66));
            System.out.println("=".repeat(66));
        }
    }

    private static void saveCombinedReport(String fileName, JobResults results) {
        try (PrintWriter pw = new PrintWriter(fileName)) {
            pw.println("SECCION,LLAVE,VELOCIDAD_PROMEDIO,MUESTRAS");
            
            // Seccion Mensual
            for (Map.Entry<String, double[]> entry : results.monthly.entrySet()) {
                double[] val = entry.getValue();
                pw.printf("MENSUAL,%s,%.2f,%d%n", entry.getKey(), val[0]/val[1], (long)val[1]);
            }
            
            // Seccion Tramos
            for (Map.Entry<String, double[]> entry : results.arcs.entrySet()) {
                double[] val = entry.getValue();
                pw.printf("TRAMO,%s,%.2f,%d%n", entry.getKey(), val[0]/val[1], (long)val[1]);
            }
        } catch (Exception e) {
            System.err.println("Error guardando reporte: " + e.getMessage());
        }
    }

    private static void exportCsv(String fileName, Map<String, double[]> data, String headerKey) {
        try (PrintWriter pw = new PrintWriter(fileName)) {
            pw.println(headerKey + ",velocidad_promedio,muestras");
            for (Map.Entry<String, double[]> entry : data.entrySet()) {
                double[] info = entry.getValue();
                double avg = info[0] / info[1];
                pw.printf("%s,%.2f,%d%n", entry.getKey(), avg, (long)info[1]);
            }
        } catch (Exception e) {
            System.err.println("Error exportando " + fileName + ": " + e.getMessage());
        }
    }

    private static List<WorkerServicePrx> connectWorkers(Communicator communicator, List<String> proxies) {
        List<WorkerServicePrx> workers = new ArrayList<>();
        for (String proxy : proxies) {
            WorkerServicePrx worker = WorkerServicePrx.checkedCast(communicator.stringToProxy(proxy));
            if (worker == null) {
                throw new IllegalArgumentException("Proxy de worker invalido: " + proxy);
            }
            System.out.println("Worker conectado: " + proxy + " ping=" + worker.ping());
            workers.add(worker);
        }
        return workers;
    }

    private static JobResults runDistributedJob(
            String csvPath,
            String jobId,
            List<WorkerServicePrx> workers) throws Exception {

        DatagramPartitioner partitioner = new DatagramPartitioner();
        PartialResults partialResults = new PartialResults();
        List<WorkerSubmitter> submitters = new ArrayList<>();
        List<List<String>> buffers = new ArrayList<>();
        for (int i = 0; i < workers.size(); i++) {
            submitters.add(new WorkerSubmitter(workers.get(i)));
            buffers.add(new ArrayList<>(REMOTE_TASK_SIZE));
        }

        int submittedTasks = 0;
        long totalLines = 0;

        CsvLoader loader = new CsvLoader(csvPath);
        try {
            while (!loader.isFinished()) {
                List<String> rawChunk = loader.nextRawChunk(READ_CHUNK_SIZE);
                for (String line : rawChunk) {
                    int workerIndex = partitioner.partitionForRawLine(line, workers.size());
                    List<String> buffer = buffers.get(workerIndex);
                    buffer.add(line);
                    totalLines++;

                    if (buffer.size() >= REMOTE_TASK_SIZE) {
                        submittedTasks = submitBuffer(jobId, submitters, buffer, workerIndex, submittedTasks);
                    }
                }

                if (totalLines > 0 && totalLines % 1_000_000 == 0) {
                    System.out.println("-> Lineas distribuidas: " + (totalLines / 1_000_000) + " Millones...");
                }
            }
        } finally {
            loader.close();
        }

        for (int workerIndex = 0; workerIndex < buffers.size(); workerIndex++) {
            List<String> buffer = buffers.get(workerIndex);
            if (!buffer.isEmpty()) {
                submittedTasks = submitBuffer(jobId, submitters, buffer, workerIndex, submittedTasks);
            }
        }

        for (WorkerSubmitter submitter : submitters) {
            submitter.finish();
        }

        System.out.println("Lectura y envio finalizados. Solicitando reduce a workers...");
        JobResults results = new JobResults();
        for (WorkerServicePrx worker : workers) {
            FinalResponse response = worker.finishJob(jobId);
            partialResults.mergeIce(results.monthly, response.monthlyData);
            partialResults.mergeIce(results.arcs, response.arcData);
        }

        System.out.println("Total lineas distribuidas: " + totalLines);
        return results;
    }

    private static int submitBuffer(
            String jobId,
            List<WorkerSubmitter> submitters,
            List<String> buffer,
            int workerIndex,
            int submittedTasks) {

        String[] lines = buffer.toArray(new String[0]);
        buffer.clear();
        int taskId = submittedTasks + 1;
        submitters.get(workerIndex).submit(jobId, new RawTask(taskId, lines));
        return taskId;
    }

    private static class WorkerSubmitter {
        private final WorkerServicePrx worker;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Deque<Future<?>> pending = new ArrayDeque<>();

        private WorkerSubmitter(WorkerServicePrx worker) {
            this.worker = worker;
        }

        private void submit(String jobId, RawTask task) {
            waitForRoom();
            pending.addLast(executor.submit(() -> worker.submitTask(jobId, task)));
        }

        private void finish() throws Exception {
            while (!pending.isEmpty()) {
                pending.removeFirst().get();
            }
            executor.shutdown();
        }

        private void waitForRoom() {
            try {
                while (pending.size() >= MAX_PENDING_TASKS_PER_WORKER) {
                    pending.removeFirst().get();
                }
            } catch (Exception e) {
                throw new RuntimeException("Error enviando tareas al worker", e);
            }
        }
    }
}
