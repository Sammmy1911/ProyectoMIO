package worker;

import SITMMIO.*;
import com.zeroc.Ice.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WorkerNode implements TaskDispatcher {
    private final ExecutorService threadPool = Executors.newFixedThreadPool(4);
    private MasterPrx master;

    public WorkerNode(MasterPrx master) {
        this.master = master;
    }

    @Override
    public void dispatchTask(WorkChunk chunk, Current current) {
        System.out.println("Received WorkChunk: " + chunk.taskId + " for Line: " + chunk.lineId);
        threadPool.submit(() -> {
            // Simulación de cálculo pesado
            double speed = Math.random() * 40 + 10; // Velocidad aleatoria entre 10 y 50 km/h
            try {
                Thread.sleep(2000); // Simular tiempo de procesamiento
                TaskResult result = new TaskResult(chunk.taskId, speed, 1000);
                master.reportResult(result);
                System.out.println("Task " + chunk.taskId + " completed and reported.");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    public static void main(String[] args) {
        InitializationData initData = new InitializationData();
        initData.properties = Util.createProperties(args);
        initData.properties.load("config.properties");

        try (Communicator communicator = Util.initialize(initData)) {
            MasterPrx master = MasterPrx.checkedCast(
                    communicator.propertyToProxy("Master.Proxy"));
            
            if (master == null) throw new Error("Invalid Master proxy");

            ObjectAdapter adapter = communicator.createObjectAdapter("WorkerAdapter");
            WorkerNode worker = new WorkerNode(master);
            adapter.add(worker, Util.stringToIdentity("TaskDispatcher"));
            adapter.activate();
            
            System.out.println("Worker Node active and connected to Master...");
            communicator.waitForShutdown();
        }
    }
}
