package worker;

import SITMMIO.*;
import com.zeroc.Ice.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WorkerNode implements TaskDispatcher {
    private final ExecutorService threadPool = Executors.newFixedThreadPool(32);
    private MasterPrx master;
    private int taskCounter = 0;

    public WorkerNode(MasterPrx master) {
        this.master = master;
    }

    @Override
    public void dispatchTask(WorkChunk chunk, Current current) {
        taskCounter++;
        if (taskCounter % 10000 == 0) {
            System.out.println("Worker has processed " + taskCounter + " tasks so far...");
        }
        threadPool.submit(() -> {
            double speed = Math.random() * 40 + 10;
            try {
                TaskResult result = new TaskResult(chunk.taskId, speed, 1000);
                master.reportResultAsync(result);
            } catch (java.lang.Exception e) {
            }
        });
    }

    public static void main(String[] args) {
        try (Communicator communicator = Util.initialize(args, "config.properties")) {
            MasterPrx master = MasterPrx.checkedCast(
                    communicator.propertyToProxy("Master.Proxy"));
            
            if (master == null) throw new Error("Invalid Master proxy");

            ObjectAdapter adapter = communicator.createObjectAdapter("WorkerAdapter");
            WorkerNode worker = new WorkerNode(master);
            
            TaskDispatcherPrx workerPrx = TaskDispatcherPrx.uncheckedCast(
                    adapter.add(worker, Util.stringToIdentity("TaskDispatcher")));
            
            adapter.activate();
            
            // Registro dinámico con el Master
            master.registerWorker(workerPrx);
            System.out.println("Worker Node active and registered with Master.");
            
            communicator.waitForShutdown();
        }
    }
}
