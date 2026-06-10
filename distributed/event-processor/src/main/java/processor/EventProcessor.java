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

public class EventProcessor implements DatagramReceiver, RealTimeStreaming {
    private final List<MonitoringSubscriberPrx> subscribers = new ArrayList<>();

    @Override
    public void receiveDatagram(Datagram data, Current current) {
        // Normalización de coordenadas
        // Ej: 34761183 -> 3.4761 (Si el factor es 10^7 o similar según el PDF/Requirements)
        // Según Requirements.md: 34761183 -> 3.4761, lo que implica dividir por 10,000,000 (10^7)
        // Sin embargo, trabajaremos con los valores normalizados si el cliente lo requiere, 
        // o los pasaremos tal cual para que el visualizador los convierta.
        
        System.out.println("Received datagram from Bus ID: " + data.busId + " Line: " + data.lineId);
        
        BusEvent event = new BusEvent();
        event.busId = data.busId;
        event.latitude = data.latitude;
        event.longitude = data.longitude;
        event.eventType = data.eventType;
        event.timestamp = data.datagramDate;

        notifySubscribers(event);
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

    public static void main(String[] args) {
        InitializationData initData = new InitializationData();
        initData.properties = Util.createProperties(args);
        initData.properties.load("config.properties");

        try (Communicator communicator = Util.initialize(initData)) {
            ObjectAdapter adapter = communicator.createObjectAdapter("EventProcessorAdapter");
            EventProcessor processor = new EventProcessor();
            adapter.add(processor, Util.stringToIdentity("DatagramReceiver"));
            adapter.add(processor, Util.stringToIdentity("RealTimeStreaming"));
            adapter.activate();
            System.out.println("Event Processor active and listening on port 10000...");
            communicator.waitForShutdown();
        }
    }
}
