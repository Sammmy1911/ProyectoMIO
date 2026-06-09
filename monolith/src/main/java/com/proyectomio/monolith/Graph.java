package com.proyectomio.monolith;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class Graph {
    private final List<Arc> arcs;
    private final Map<String, List<Arc>> grid;
    private final double cellDegrees;

    private Graph(List<Arc> arcs, Map<String, List<Arc>> grid, double cellDegrees) {
        this.arcs = arcs;
        this.grid = grid;
        this.cellDegrees = cellDegrees;
    }

    static Graph load(Path path, double cellDegrees) throws IOException {
        List<Arc> arcs = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("El archivo de arcos está vacío: " + path);
            }

            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                String[] p = Csv.split(line);
                if (p.length < 6) {
                    throw new IOException("Arco inválido en línea " + lineNumber + ": " + line);
                }
                arcs.add(new Arc(
                        p[0],
                        p[1],
                        Double.parseDouble(p[2]),
                        Double.parseDouble(p[3]),
                        Double.parseDouble(p[4]),
                        Double.parseDouble(p[5])
                ));
            }
        }

        Map<String, List<Arc>> grid = new HashMap<>();
        for (Arc arc : arcs) {
            int minLatCell = cell(arc.minLat, cellDegrees);
            int maxLatCell = cell(arc.maxLat, cellDegrees);
            int minLonCell = cell(arc.minLon, cellDegrees);
            int maxLonCell = cell(arc.maxLon, cellDegrees);
            for (int lat = minLatCell - 1; lat <= maxLatCell + 1; lat++) {
                for (int lon = minLonCell - 1; lon <= maxLonCell + 1; lon++) {
                    grid.computeIfAbsent(key(lat, lon), ignored -> new ArrayList<>()).add(arc);
                }
            }
        }

        return new Graph(Collections.unmodifiableList(arcs), grid, cellDegrees);
    }

    List<Arc> arcs() {
        return arcs;
    }

    Arc findNearestArc(String routeId, double lat, double lon, double maxDistanceMeters, boolean ignoreRoute) {
        int latCell = cell(lat, cellDegrees);
        int lonCell = cell(lon, cellDegrees);
        Arc best = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (int dLat = -1; dLat <= 1; dLat++) {
            for (int dLon = -1; dLon <= 1; dLon++) {
                List<Arc> candidates = grid.get(key(latCell + dLat, lonCell + dLon));
                if (candidates == null) {
                    continue;
                }
                for (Arc arc : candidates) {
                    if (!ignoreRoute && !routeId.isBlank() && !arc.routeId.equals(routeId)) {
                        continue;
                    }
                    double distance = Geo.distancePointToSegmentMeters(lat, lon, arc);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = arc;
                    }
                }
            }
        }

        return bestDistance <= maxDistanceMeters ? best : null;
    }

    private static int cell(double value, double cellDegrees) {
        return (int) Math.floor(value / cellDegrees);
    }

    private static String key(int latCell, int lonCell) {
        return latCell + ":" + lonCell;
    }
}
