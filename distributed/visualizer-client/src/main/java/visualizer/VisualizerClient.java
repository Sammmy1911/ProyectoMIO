package visualizer;

import SITMMIO.BusEvent;
import SITMMIO.MonitoringSubscriber;
import SITMMIO.MonitoringSubscriberPrx;
import SITMMIO.RealTimeStreamingPrx;
import SITMMIO.QueryProviderPrx;
import SITMMIO.SpeedReport;
import com.zeroc.Ice.*;

public class VisualizerClient implements MonitoringSubscriber {

    @Override
    public void updateLocation(BusEvent event, Current current) {
        System.out.println("[MAP] Bus " + event.busId + " at (" + (event.latitude/10000000.0) + ", " + (event.longitude/10000000.0) + ")");
    }

    public static void main(String[] args) {
        try (Communicator communicator = Util.initialize(args, "config.properties")) {
            // Subscribe to real-time events
            RealTimeStreamingPrx streaming = RealTimeStreamingPrx.checkedCast(
                    communicator.propertyToProxy("RealTimeStreaming.Proxy"));
            
            if (streaming != null) {
                ObjectAdapter adapter = communicator.createObjectAdapter("VisualizerCallbackAdapter");
                adapter.add(new VisualizerClient(), Util.stringToIdentity("VisualizerCallback"));
                adapter.activate();
                
                MonitoringSubscriberPrx subPrx = MonitoringSubscriberPrx.uncheckedCast(
                        adapter.createProxy(Util.stringToIdentity("VisualizerCallback")));
                
                streaming.subscribe(subPrx);
                System.out.println("Subscribed to Real-Time Streaming.");
            }

            // Consult historical reports (Component Query Engine)
            QueryProviderPrx queryProvider = QueryProviderPrx.checkedCast(
                    communicator.propertyToProxy("QueryProvider.Proxy"));
            
            if (queryProvider != null) {
                System.out.println("Consulting historical reports...");
                SpeedReport[] reports = queryProvider.getAllSpeedReports();
                for (SpeedReport r : reports) {
                    System.out.println("Report: Line " + r.lineId + " Month " + r.month + " Avg Speed: " + r.averageSpeed);
                }
            }

            System.out.println("Visualizer Client active. Press Ctrl+C to exit.");
            communicator.waitForShutdown();
        }
    }
}
