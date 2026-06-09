package com.proyectomio.monolith;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class SyntheticDatagramGenerator {
    private SyntheticDatagramGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4 || "--help".equals(args[0])) {
            System.out.println("""
                    Uso:
                      java -cp monolith/out com.proyectomio.monolith.SyntheticDatagramGenerator <arcos.csv> <salida.csv> <datagramas> <buses>

                    Ejemplo:
                      java -cp monolith/out com.proyectomio.monolith.SyntheticDatagramGenerator monolith/sample/arcs.csv monolith/sample/generated_100000.csv 100000 200
                    """);
            return;
        }

        Path arcsPath = Path.of(args[0]);
        Path outputPath = Path.of(args[1]);
        long datagrams = Long.parseLong(args[2]);
        int buses = Integer.parseInt(args[3]);
        List<Arc> arcs = Graph.load(arcsPath, 0.002).arcs();
        generate(arcs, outputPath, datagrams, buses);
        System.out.println("Datagramas generados: " + outputPath.toAbsolutePath());
    }

    private static void generate(List<Arc> arcs, Path outputPath, long datagrams, int buses) throws IOException {
        Files.createDirectories(outputPath.toAbsolutePath().getParent());
        List<BusState> states = new ArrayList<>(buses);
        for (int i = 0; i < buses; i++) {
            Arc arc = arcs.get(i % arcs.size());
            states.add(new BusState("BUS-" + String.format(Locale.US, "%06d", i + 1), arc, i % arcs.size()));
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("bus_id,route_id,timestamp,latitude,longitude");
            writer.newLine();
            long baseEpoch = 1_767_225_600L; // 2026-01-01T00:00:00Z
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (long i = 0; i < datagrams; i++) {
                BusState state = states.get((int) (i % buses));
                if (state.progress >= 1.0) {
                    state.arcIndex = (state.arcIndex + 1) % arcs.size();
                    state.arc = arcs.get(state.arcIndex);
                    state.progress = 0.0;
                }
                state.progress += 0.08 + random.nextDouble(0.015);
                double p = Math.min(state.progress, 1.0);
                double lat = state.arc.fromLat + (state.arc.toLat - state.arc.fromLat) * p + random.nextDouble(-0.00003, 0.00003);
                double lon = state.arc.fromLon + (state.arc.toLon - state.arc.fromLon) * p + random.nextDouble(-0.00003, 0.00003);
                long epoch = baseEpoch + (i / buses) * 30L;
                writer.write(state.busId);
                writer.write(',');
                writer.write(state.arc.routeId);
                writer.write(',');
                writer.write(formatTimestamp(epoch));
                writer.write(',');
                writer.write(String.format(Locale.US, "%.6f,%.6f", lat, lon));
                writer.newLine();
            }
        }
    }

    private static String formatTimestamp(long epochSecond) {
        long days = Math.floorDiv(epochSecond, 86_400L);
        int secondsOfDay = (int) Math.floorMod(epochSecond, 86_400L);
        int hour = secondsOfDay / 3_600;
        int minute = (secondsOfDay % 3_600) / 60;
        int second = secondsOfDay % 60;

        int[] ymd = civilFromEpochDay(days);
        return String.format(Locale.US, "%04d-%02d-%02dT%02d:%02d:%02d", ymd[0], ymd[1], ymd[2], hour, minute, second);
    }

    private static int[] civilFromEpochDay(long epochDay) {
        long zeroDay = epochDay + 719_528L - 60L;
        long adjust = 0L;
        if (zeroDay < 0L) {
            long adjustCycles = (zeroDay + 1L) / 146_097L - 1L;
            adjust = adjustCycles * 400L;
            zeroDay += -adjustCycles * 146_097L;
        }
        long yearEst = (400L * zeroDay + 591L) / 146_097L;
        long doyEst = zeroDay - (365L * yearEst + yearEst / 4L - yearEst / 100L + yearEst / 400L);
        if (doyEst < 0L) {
            yearEst--;
            doyEst = zeroDay - (365L * yearEst + yearEst / 4L - yearEst / 100L + yearEst / 400L);
        }
        yearEst += adjust;
        int marchDoy0 = (int) doyEst;
        int marchMonth0 = (marchDoy0 * 5 + 2) / 153;
        int month = (marchMonth0 + 2) % 12 + 1;
        int day = marchDoy0 - (marchMonth0 * 306 + 5) / 10 + 1;
        yearEst += marchMonth0 / 10;
        return new int[]{(int) yearEst, month, day};
    }

    private static final class BusState {
        final String busId;
        Arc arc;
        int arcIndex;
        double progress;

        BusState(String busId, Arc arc, int arcIndex) {
            this.busId = busId;
            this.arc = arc;
            this.arcIndex = arcIndex;
        }
    }
}
