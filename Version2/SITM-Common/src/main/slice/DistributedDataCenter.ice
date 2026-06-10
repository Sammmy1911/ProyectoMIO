module datacenter
{
    sequence<string> StringSeq;
    sequence<int> IntSeq;

    struct RawTask
    {
        int id;
        StringSeq lines;
    };

    struct TramoInfo {
        double sumaVelocidad;
        long conteo;
    }

    dictionary<string, TramoInfo> ResultsMap;

    struct FinalResponse {
        ResultsMap monthlyData;
        ResultsMap arcData;
    }

    interface WorkerService
    {
        string ping();
        void beginJob(string jobId, IntSeq activeLineIds);
        void submitTask(string jobId, RawTask task);
        FinalResponse finishJob(string jobId);
        void cancelJob(string jobId);
    };
};
