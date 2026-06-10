module SITMMIO {
    struct GPSPoint {
        long latitude;
        long longitude;
    };

    struct Datagram {
        int eventType;
        string registerDate;
        int stopId;
        int odometer;
        long latitude;
        long longitude;
        int taskId;
        int lineId;
        int tripId;
        string datagramDate;
        int busId;
    };

    struct BusEvent {
        int busId;
        long latitude;
        long longitude;
        int eventType;
        string timestamp;
    };

    sequence<BusEvent> BusEventSeq;

    interface DatagramReceiver {
        void receiveDatagram(Datagram data);
    };

    interface MonitoringSubscriber {
        void updateLocation(BusEvent event);
    };

    interface RealTimeStreaming {
        void subscribe(MonitoringSubscriber* sub);
        void unsubscribe(MonitoringSubscriber* sub);
    };

    interface ArchiveService {
        void archive(Datagram data);
    };

    struct SpeedReport {
        int lineId;
        string month;
        double averageSpeed;
    };

    sequence<SpeedReport> SpeedReportSeq;

    interface QueryProvider {
        SpeedReport getSpeedReport(int lineId, string month);
        SpeedReportSeq getAllSpeedReports();
    };

    struct WorkChunk {
        int taskId;
        int lineId;
        string month;
        // In a real scenario, this would point to a file or a range of records
        string dataReference; 
    };

    struct TaskResult {
        int taskId;
        double calculatedSpeed;
        int processedRecords;
    };

    interface TaskDispatcher {
        void dispatchTask(WorkChunk chunk);
    };

    interface Master {
        void reportResult(TaskResult result);
        void registerWorker(TaskDispatcher* worker);
    };
};
