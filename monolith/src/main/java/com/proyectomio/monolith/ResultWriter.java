package com.proyectomio.monolith;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class ResultWriter {
    private ResultWriter() {
    }

    static void write(
            Path outputDir,
            List<Arc> arcs,
            Map<String, ArcAccumulator> accumulators,
            Metrics metrics,
            int busesSeen,
            long minSamplesPerArc
    ) throws IOException {
        Files.createDirectories(outputDir);
        writeArcSpeeds(outputDir.resolve("arc_speeds.csv"), arcs, accumulators, minSamplesPerArc);
        Files.writeString(
                outputDir.resolve("metrics.txt"),
                metrics.report(arcs.size(), busesSeen),
                StandardCharsets.UTF_8
        );
    }

    private static void writeArcSpeeds(
            Path output,
            List<Arc> arcs,
            Map<String, ArcAccumulator> accumulators,
            long minSamplesPerArc
    ) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("arc_id,route_id,from_lat,from_lon,to_lat,to_lon,arc_length_meters,samples,avg_speed_kmh,min_speed_kmh,max_speed_kmh,total_distance_meters,total_duration_seconds,status");
            writer.newLine();
            arcs.stream()
                    .sorted(Comparator.comparing((Arc arc) -> arc.routeId).thenComparing(arc -> arc.id))
                    .forEach(arc -> {
                        try {
                            writer.write(accumulators.get(arc.id).toCsv(arc, minSamplesPerArc));
                            writer.newLine();
                        } catch (IOException e) {
                            throw new ResultWriteException(e);
                        }
                    });
        } catch (ResultWriteException ex) {
            throw ex.unwrap();
        }
    }

    private static final class ResultWriteException extends RuntimeException {
        ResultWriteException(IOException cause) {
            super(cause);
        }

        IOException unwrap() {
            return (IOException) getCause();
        }
    }
}
