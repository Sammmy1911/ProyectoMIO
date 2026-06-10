package co.edu.icesi.sitmmio.datacenter.service;

public interface SITMObserver {
    void onBusMoved(int busId, double latitude, double longitude, int lineId);
}
