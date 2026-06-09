package com.proyectomio.monolith;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class MonolithApp {
    private MonolithApp() {
    }

    public static void main(String[] args) throws Exception {
        Arguments arguments = Arguments.parse(args);
        if (arguments.help) {
            Arguments.printHelp();
            return;
        }

        Graph graph = Graph.load(arguments.arcsPath, arguments.cellDegrees);
        MonolithicSpeedProcessor processor = new MonolithicSpeedProcessor(
                graph,
                arguments.parser(),
                arguments.maxGpsDistanceMeters,
                arguments.maxSpeedKmh,
                arguments.minSegmentSeconds,
                arguments.maxSegmentSeconds,
                arguments.minSamplesPerArc,
                arguments.ignoreRoute,
                arguments.limit
        );

        ProcessingResult result = processor.process(arguments.datagramsPath);
        ResultWriter.write(
                arguments.outputDir,
                graph.arcs(),
                result.accumulators(),
                result.metrics(),
                result.busesSeen(),
                arguments.minSamplesPerArc
        );

        System.out.println("Procesamiento monolítico finalizado.");
        System.out.println("Resultados: " + arguments.outputDir.resolve("arc_speeds.csv").toAbsolutePath());
        System.out.println("Métricas: " + arguments.outputDir.resolve("metrics.txt").toAbsolutePath());
        System.out.printf("Throughput: %.2f datagramas/segundo%n",
                result.metrics().totalDatagrams / Math.max(result.metrics().elapsedSeconds(), 0.000001));
    }

    private static final class Arguments {
        private boolean help;
        private Path arcsPath;
        private Path datagramsPath;
        private Path outputDir = Path.of("monolith", "output");
        private double maxGpsDistanceMeters = 80.0;
        private double maxSpeedKmh = 90.0;
        private long minSegmentSeconds = 5;
        private long maxSegmentSeconds = 300;
        private long minSamplesPerArc = 2;
        private double cellDegrees = 0.002;
        private String format = "default";
        private boolean ignoreRoute;
        private long limit = -1;

        static Arguments parse(String[] args) {
            Arguments parsed = new Arguments();
            Map<String, String> values = new HashMap<>();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--help".equals(arg) || "-h".equals(arg)) {
                    parsed.help = true;
                    return parsed;
                }
                if (!arg.startsWith("--") || i + 1 >= args.length) {
                    throw new IllegalArgumentException("Argumento inválido: " + arg);
                }
                values.put(arg.substring(2), args[++i]);
            }

            parsed.arcsPath = requiredPath(values, "arcs");
            parsed.datagramsPath = requiredPath(values, "datagrams");
            if (values.containsKey("out")) {
                parsed.outputDir = Path.of(values.get("out"));
            }
            if (values.containsKey("max-gps-distance-meters")) {
                parsed.maxGpsDistanceMeters = Double.parseDouble(values.get("max-gps-distance-meters"));
            }
            if (values.containsKey("max-speed-kmh")) {
                parsed.maxSpeedKmh = Double.parseDouble(values.get("max-speed-kmh"));
            }
            if (values.containsKey("min-segment-seconds")) {
                parsed.minSegmentSeconds = Long.parseLong(values.get("min-segment-seconds"));
            }
            if (values.containsKey("max-segment-seconds")) {
                parsed.maxSegmentSeconds = Long.parseLong(values.get("max-segment-seconds"));
            }
            if (values.containsKey("min-samples-per-arc")) {
                parsed.minSamplesPerArc = Long.parseLong(values.get("min-samples-per-arc"));
            }
            if (values.containsKey("cell-degrees")) {
                parsed.cellDegrees = Double.parseDouble(values.get("cell-degrees"));
            }
            if (values.containsKey("format")) {
                parsed.format = values.get("format").toLowerCase();
            }
            if (values.containsKey("ignore-route")) {
                parsed.ignoreRoute = Boolean.parseBoolean(values.get("ignore-route"));
            }
            if (values.containsKey("limit")) {
                parsed.limit = Long.parseLong(values.get("limit"));
            }
            return parsed;
        }

        DatagramParser parser() {
            return switch (format) {
                case "default" -> new DefaultDatagramParser();
                case "minipilot" -> new MiniPilotDatagramParser();
                default -> throw new IllegalArgumentException("Formato de datagrama no soportado: " + format);
            };
        }

        static void printHelp() {
            System.out.println("""
                    Uso:
                      java -cp monolith/out com.proyectomio.monolith.MonolithApp --arcs <arcos.csv> --datagrams <datagramas.csv> --out <carpeta>

                    Opciones:
                      --max-gps-distance-meters <n>  Distancia máxima para asociar GPS a arco. Default: 80
                      --max-speed-kmh <n>            Umbral para descartar velocidades anómalas. Default: 90
                      --min-segment-seconds <n>      Duración mínima entre puntos consecutivos. Default: 5
                      --max-segment-seconds <n>      Duración máxima entre puntos consecutivos. Default: 300
                      --min-samples-per-arc <n>      Muestras mínimas para publicar un arco como OK. Default: 2
                      --cell-degrees <n>             Tamaño de celda del índice espacial. Default: 0.002
                      --format <default|minipilot>    Formato del CSV de datagramas. Default: default
                      --ignore-route <true|false>     Ignora route_id al asociar GPS con arcos. Default: false
                      --limit <n>                     Procesa solo los primeros n datagramas. Default: sin límite
                    """);
        }

        private static Path requiredPath(Map<String, String> values, String key) {
            String value = values.get(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Falta argumento obligatorio --" + key);
            }
            return Path.of(value);
        }
    }
}
