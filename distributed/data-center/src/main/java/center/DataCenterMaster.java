package center;

import SITMMIO.*;
import com.zeroc.Ice.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class DataCenterMaster {
    private final ConcurrentHashMap<Integer, TaskResult> results = new ConcurrentHashMap<>();
    private final List<SpeedReport> consolidatedReports = new ArrayList<>();
    private TaskDispatcherPrx worker;
    private int archiveCounter = 0;

    public void setWorker(TaskDispatcherPrx worker) {
        this.worker = worker;
    }

    // Servant para el Master (Recibe resultados y registros de los Workers)
    class MasterI implements Master {
        @Override
        public void reportResult(TaskResult result, Current current) {
            results.put(result.taskId, result);
            
            SpeedReport report = new SpeedReport();
            report.lineId = result.taskId;
            report.month = "MAY-19";
            report.averageSpeed = result.calculatedSpeed;
            consolidatedReports.add(report);
            
            if (results.size() % 10000 == 0) {
                System.out.println("Received 10,000 analysis results...");
            }
        }

        @Override
        public void registerWorker(TaskDispatcherPrx workerPrx, Current current) {
            setWorker(workerPrx);
            System.out.println("New Worker Node registered successfully.");
        }
    }

    // Servant para ArchiveService (Recibe datagramas del EventProcessor)
    class ArchiveServiceI implements ArchiveService {
        @Override
        public void archive(Datagram data, Current current) {
            archiveCounter++;
            if (archiveCounter % 10000 == 0) {
                System.out.println("Archived 10,000 datagrams total.");
            }
            
            // Ahora disparamos tareas para TODOS los buses
            if (worker != null) {
                WorkChunk chunk = new WorkChunk(data.busId, data.lineId, "MAY-19", "data_ref_" + data.busId);
                worker.dispatchTaskAsync(chunk);
            }
        }
    }

    // Servant para QueryProvider (Atiende consultas del Visualizer)
    class QueryProviderI implements QueryProvider {
        @Override
        public SpeedReport getSpeedReport(int lineId, String month, Current current) {
            return consolidatedReports.stream()
                    .filter(r -> r.lineId == lineId && r.month.equals(month))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public SpeedReport[] getAllSpeedReports(Current current) {
            return consolidatedReports.toArray(new SpeedReport[0]);
        }
    }

    public void exportToCSV() {
        System.out.println("Exporting " + consolidatedReports.size() + " results to CSV...");
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.File("results_summary.csv"))) {
            writer.println("LineID,Month,AverageSpeed");
            for (SpeedReport report : consolidatedReports) {
                writer.println(report.lineId + "," + report.month + "," + report.averageSpeed);
            }
            System.out.println("Export successful: results_summary.csv");
        } catch (java.io.IOException e) {
            System.err.println("Error exporting to CSV: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try (Communicator communicator = Util.initialize(args, "config.properties")) {
            ObjectAdapter adapter = communicator.createObjectAdapter("DataCenterAdapter");
            DataCenterMaster masterInstance = new DataCenterMaster();
            
            adapter.add(masterInstance.new MasterI(), Util.stringToIdentity("Master"));
            adapter.add(masterInstance.new ArchiveServiceI(), Util.stringToIdentity("ArchiveService"));
            adapter.add(masterInstance.new QueryProviderI(), Util.stringToIdentity("QueryProvider"));
            
            adapter.activate();
            System.out.println("Data Center (Master) active on port 10001...");
            System.out.println("PRESS ENTER AT ANY TIME TO EXPORT RESULTS TO CSV AND EXIT");
            
            // Hilo para escuchar el teclado sin bloquear Ice
            new Thread(() -> {
                try {
                    System.in.read();
                    masterInstance.exportToCSV();
                    System.exit(0);
                } catch (Exception e) {}
            }).start();

            communicator.waitForShutdown();
        }
    }
}
