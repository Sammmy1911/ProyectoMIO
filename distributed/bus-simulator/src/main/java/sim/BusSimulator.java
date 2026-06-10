package sim;

import SITMMIO.Datagram;
import SITMMIO.DatagramReceiverPrx;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.Util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class BusSimulator {
    public static void main(String[] args) {
        InitializationData initData = new InitializationData();
        initData.properties = Util.createProperties(args);
        initData.properties.load("config.properties");

        try (Communicator communicator = Util.initialize(initData)) {
            DatagramReceiverPrx receiver = DatagramReceiverPrx.checkedCast(
                    communicator.propertyToProxy("DatagramReceiver.Proxy"));
            
            if (receiver == null) {
                throw new Error("Invalid proxy");
            }

            String csvFile = "../doc/BaseData/datagrams-MiniPilot.csv";
            String line;
            String cvsSplitBy = ",";

            System.out.println("Starting simulation...");
            try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
                while ((line = br.readLine()) != null) {
                    String[] data = line.split(cvsSplitBy);
                    if (data.length < 12) continue;

                    Datagram datagram = new Datagram();
                    datagram.eventType = Integer.parseInt(data[0]);
                    datagram.registerDate = data[1];
                    datagram.stopId = Integer.parseInt(data[2]);
                    datagram.odometer = Integer.parseInt(data[3]);
                    datagram.latitude = Long.parseLong(data[4]);
                    datagram.longitude = Long.parseLong(data[5]);
                    datagram.taskId = Integer.parseInt(data[6]);
                    datagram.lineId = Integer.parseInt(data[7]);
                    datagram.tripId = Integer.parseInt(data[8]);
                    // data[9] unknown1
                    datagram.datagramDate = data[10];
                    datagram.busId = Integer.parseInt(data[11]);

                    receiver.receiveDatagram(datagram);
                    
                    // Simulating real-time by adding a small delay if needed
                    // Thread.sleep(10); 
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Simulation finished.");
        }
    }
}
