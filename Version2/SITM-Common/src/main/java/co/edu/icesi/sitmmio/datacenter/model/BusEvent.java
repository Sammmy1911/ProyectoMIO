package co.edu.icesi.sitmmio.datacenter.model;

import java.time.LocalDateTime;

public class BusEvent {

    private final int busId;
    private final int lineId;
    private final int tripId;
    private final int stopId;
    private final double latitude;   // ya convertida a grados decimales
    private final double longitude;  // ya convertida a grados decimales
    private final LocalDateTime datagramDate;

    public BusEvent(int busId, int lineId, int tripId, int stopId,
                    double latitude, double longitude, LocalDateTime datagramDate) {
        this.busId = busId;
        this.lineId = lineId;
        this.tripId = tripId;
        this.stopId = stopId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.datagramDate = datagramDate;
    }

    public int getBusId()               { return busId; }
    public int getLineId()              { return lineId; }
    public int getTripId()              { return tripId; }
    public int getStopId()              { return stopId; }
    public double getLatitude()         { return latitude; }
    public double getLongitude()        { return longitude; }
    public LocalDateTime getDatagramDate() { return datagramDate; }
}