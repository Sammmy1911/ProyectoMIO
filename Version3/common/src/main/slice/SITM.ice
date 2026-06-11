module co {
    module edu {
        module uicesi {
            module sitmmio {
                module datacenter {

                    struct BusEvent {
                        int busId;
                        int lineId;
                        int tripId;
                        int stopId;
                        double latitude;
                        double longitude;
                        long timestamp;
                    };

                    dictionary<string, double> MonthlyData;
                    dictionary<string, double> SampleCount;

                    struct ProcessingResult {
                        MonthlyData monthlyAverages;
                        SampleCount samples;
                    };

                    // DTO para métricas de rendimiento
                    struct PerformanceMetrics {
                        double ingestionTimeSec;
                        double processingTimeSec;
                        double totalTimeSec;
                        double dataSizeMB;
                    };

                    sequence<byte> ByteBlock;
                    sequence<int> IntList;

                    interface SITMObserver {
                        void onBusMoved(int busId, double lat, double lon, int lineId, long timestamp);
                        void onProcessingComplete(PerformanceMetrics metrics); 
                    };

                    interface Worker {
                        void initialize(IntList activeLines);
                        ProcessingResult processChunk(ByteBlock data);
                    };

                    interface SITMMaster {
                        void initialize(IntList activeLines);
                        void processEvents(ByteBlock data);
                        void finishIngestion(long startTimeMillis); 
                        void subscribe(SITMObserver* observer);
                        ProcessingResult getMonthlyReport();
                    };
                };
            };
        };
    };
};
