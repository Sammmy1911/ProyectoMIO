package co.edu.icesi.sitmmio.datacenter.distributed;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

public class WorkerServer {

    public static void main(String[] args) {
        com.zeroc.Ice.InitializationData initData = new com.zeroc.Ice.InitializationData();
        initData.properties = Util.createProperties();
        initData.properties.setProperty("Ice.MessageSizeMax", "20480");

        try (Communicator communicator = Util.initialize(args, initData)) {
            // Si hay argumentos posicionales, se usan como respaldo
            String identity = args.length >= 3 ? args[2] : "WorkerService";

            // createObjectAdapter buscará "WorkerAdapter.Endpoints" en el archivo .config
            ObjectAdapter adapter = communicator.createObjectAdapter("WorkerAdapter");
            adapter.add(new WorkerServiceI(), Util.stringToIdentity(identity));
            adapter.activate();

            System.out.println("Worker ICE activo y escuchando según configuración...");
            communicator.waitForShutdown();
        } catch (Exception e) {
            System.err.println("Error al iniciar el Worker: " + e.getMessage());
            System.err.println("Asegúrate de pasar --Ice.Config=../config.worker1");
        }
    }
}
