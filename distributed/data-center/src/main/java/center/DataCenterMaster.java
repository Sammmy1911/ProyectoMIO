package center;

import SITMMIO.*;
import com.zeroc.Ice.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class DataCenterMaster implements Master, ArchiveService, QueryProvider {
    private final ConcurrentHashMap<Integer, TaskResult> results = new ConcurrentHashMap<>();
    private final List<SpeedReport> consolidatedReports = new ArrayList<>();

    @Override
    public void archive(Datagram data, Current current) {
        // Persistencia (simulada o en BD)
        System.out.println("Archiving datagram from bus: " + data.busId);
    }

    @Override
    public void reportResult(TaskResult result, Current current) {
        results.put(result.taskId, result);
        System.out.println("Received result for Task ID: " + result.taskId + " Speed: " + result.calculatedSpeed);
        
        // Lógica de consolidación
        SpeedReport report = new SpeedReport();
        report.lineId = result.taskId; // Simplificación para el ejemplo
        report.month = "MAY-19";
        report.averageSpeed = result.calculatedSpeed;
        consolidatedReports.add(report);
    }

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

    public static void main(String[] args) {
        InitializationData initData = new InitializationData();
        initData.properties = Util.createProperties(args);
        initData.properties.load("config.properties");

        try (Communicator communicator = Util.initialize(initData)) {
            ObjectAdapter adapter = communicator.createObjectAdapter("DataCenterAdapter");
            DataCenterMaster master = new DataCenterMaster();
            adapter.add(master, Util.stringToIdentity("Master"));
            adapter.add(master, Util.stringToIdentity("ArchiveService"));
            adapter.add(master, Util.stringToIdentity("QueryProvider"));
            adapter.activate();
            System.out.println("Data Center (Master) active on port 10001...");
            communicator.waitForShutdown();
        }
    }
}
