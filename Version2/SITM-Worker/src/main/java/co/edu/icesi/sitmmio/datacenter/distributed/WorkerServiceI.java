package co.edu.icesi.sitmmio.datacenter.distributed;

import co.edu.icesi.sitmmio.datacenter.model.BusEvent;
import co.edu.icesi.sitmmio.datacenter.model.ProcessingResult;
import co.edu.icesi.sitmmio.datacenter.service.PartialResults;
import co.edu.icesi.sitmmio.datacenter.service.SpeedCalculator;
import com.zeroc.Ice.Current;
import datacenter.FinalResponse;
import datacenter.RawTask;
import datacenter.WorkerService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WorkerServiceI implements WorkerService {

    private final Map<String, JobState> jobs = new HashMap<>();
    private final SpeedCalculator calculator = new SpeedCalculator();
    private final PartialResults partialResultsHelper = new PartialResults();

    @Override
    public String ping(Current current) {
        return "ok";
    }

    @Override
    public synchronized void beginJob(String jobId, int[] activeLineIds, Current current) {
        Set<Integer> activeLines = new HashSet<>();
        for (int lineId : activeLineIds) {
            activeLines.add(lineId);
        }
        jobs.put(jobId, new JobState(activeLines));
        System.out.println("Job iniciado: " + jobId + " lineas activas=" + activeLines.size());
    }

    @Override
    public synchronized void submitTask(String jobId, RawTask task, Current current) {
        JobState state = requireJob(jobId);
        int accepted = 0;
        Map<String, List<BusEvent>> taskTrajectories = new HashMap<>();

        for (String line : task.lines) {
            BusEvent event = calculator.parseLine(line, state.activeLineIds);
            if (event != null) {
                String key = event.getBusId() + "_" + event.getTripId();
                taskTrajectories.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
                accepted++;
            }
        }

        processTaskTrajectories(state, taskTrajectories);
        state.rawLines += task.lines.length;
        state.acceptedEvents += accepted;
        if (task.id % 25 == 0) {
            System.out.println("Job " + jobId + " task=" + task.id + " aceptados=" + state.acceptedEvents);
        }
    }

    @Override
    public synchronized FinalResponse finishJob(String jobId, Current current) {
        JobState state = requireJob(jobId);
        jobs.remove(jobId);
        System.out.println("Job finalizado: " + jobId
                + " lineas=" + state.rawLines
                + " eventos=" + state.acceptedEvents
                + " trayectorias=" + state.lastEventsByTrajectory.size()
                + " resultadosMensuales=" + state.monthlyResults.size()
                + " resultadosArcos=" + state.arcResults.size());

        FinalResponse response = new FinalResponse();
        response.monthlyData = partialResultsHelper.toIceMap(state.monthlyResults);
        response.arcData = partialResultsHelper.toIceMap(state.arcResults);
        return response;
    }

    @Override
    public synchronized void cancelJob(String jobId, Current current) {
        jobs.remove(jobId);
        System.out.println("Job cancelado: " + jobId);
    }

    private JobState requireJob(String jobId) {
        JobState state = jobs.get(jobId);
        if (state == null) {
            throw new IllegalArgumentException("Job no encontrado: " + jobId);
        }
        return state;
    }

    private void processTaskTrajectories(JobState state, Map<String, List<BusEvent>> taskTrajectories) {
        for (Map.Entry<String, List<BusEvent>> entry : taskTrajectories.entrySet()) {
            String trajectoryKey = entry.getKey();
            List<BusEvent> trajectory = entry.getValue();
            trajectory.sort(Comparator.comparing(BusEvent::getDatagramDate));

            BusEvent lastEvent = state.lastEventsByTrajectory.get(trajectoryKey);
            if (lastEvent != null) {
                trajectory.add(0, lastEvent);
            }

            ProcessingResult taskResult = calculator.processTrajectories(List.of(trajectory));
            partialResultsHelper.merge(state.monthlyResults, taskResult.getMonthlyData());
            partialResultsHelper.merge(state.arcResults, taskResult.getArcData());

            state.lastEventsByTrajectory.put(trajectoryKey, trajectory.get(trajectory.size() - 1));
        }
    }

    private static class JobState {
        private final Set<Integer> activeLineIds;
        private final Map<String, BusEvent> lastEventsByTrajectory = new HashMap<>();
        private final Map<String, double[]> monthlyResults = new HashMap<>();
        private final Map<String, double[]> arcResults = new HashMap<>();
        private long rawLines;
        private long acceptedEvents;

        private JobState(Set<Integer> activeLineIds) {
            this.activeLineIds = activeLineIds;
        }
    }
}
