package processor;

import SITMMIO.Datagram;
import SITMMIO.DatagramReceiver;
import SITMMIO.BusEvent;
import SITMMIO.MonitoringSubscriberPrx;
import SITMMIO.RealTimeStreaming;
import com.zeroc.Ice.Current;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.Util;
import com.zeroc.Ice.ObjectAdapter;

import java.util.ArrayList;
import java.util.List;

public class EventProcessor {
    private final List<MonitoringSubscriberPrx> subscribers = new ArrayList<>();
    private SITMMIO.ArchiveServicePrx archiver;
    private int counter = 0;

    public EventProcessor(SITMMIO.ArchiveServicePrx archiver) {
        this.archiver = archiver;
    }

    // Servant para recibir Datagramas (del Simulador)
    class DatagramReceiverI implements DatagramReceiver {
        @Override
        public void receiveDatagram(Datagram data, Current current) {
            counter++;
            if (counter % 100 == 0) {
                System.out.println("Processed 100 datagrams. Last Bus ID: " + data.busId);
            }
            
            if (archiver != null) {
                archiver.archiveAsync(data);
            }

            BusEvent event = new BusEvent();
            event.busId = data.busId;
            event.latitude = data.latitude;
            event.longitude = data.longitude;
            event.eventType = data.eventType;
            event.timestamp = data.datagramDate;

            notifySubscribers(event);
        }
    }

    // Servant para el Streaming en Tiempo Real (para el Visualizador)
    class RealTimeStreamingI implements RealTimeStreaming {
        @Override
        public synchronized void subscribe(MonitoringSubscriberPrx sub, Current current) {
            subscribers.add(sub);
            System.out.println("New subscriber added.");
        }

        @Override
        public synchronized void unsubscribe(MonitoringSubscriberPrx sub, Current current) {
            subscribers.remove(sub);
            System.out.println("Subscriber removed.");
        }
    }

    private synchronized void notifySubscribers(BusEvent event) {
        subscribers.removeIf(sub -> {
            try {
                sub.updateLocation(event);
                return false;
            } catch (com.zeroc.Ice.LocalException e) {
                return true;
            }
        });
    }

    public static void main(String[] args) {
        try (Communicator communicator = Util.initialize(args, "config.properties")) {
            SITMMIO.ArchiveServicePrx archiver = SITMMIO.ArchiveServicePrx.checkedCast(
                    communicator.propertyToProxy("ArchiveService.Proxy"));
            
            ObjectAdapter adapter = communicator.createObjectAdapter("EventProcessorAdapter");
            EventProcessor instance = new EventProcessor(archiver);
            
            adapter.add(instance.new DatagramReceiverI(), Util.stringToIdentity("DatagramReceiver"));
            adapter.add(instance.new RealTimeStreamingI(), Util.stringToIdentity("RealTimeStreaming"));
            
            adapter.activate();
            System.out.println("Event Processor active and listening on port 10000...");
            communicator.waitForShutdown();
        }
    }
}
