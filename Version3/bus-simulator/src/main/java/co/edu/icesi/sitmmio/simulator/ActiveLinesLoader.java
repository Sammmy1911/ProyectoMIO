package co.edu.icesi.sitmmio.simulator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ActiveLinesLoader {

    public Set<Integer> loadActiveLineIds(String filePath) throws IOException {
        Set<Integer> activeLines = new HashSet<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean firstLine = true;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;

                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length < 1)
                    continue;

                try {
                    int lineId = Integer.parseInt(parts[0].trim().replace("\"", ""));
                    if (lineId != -1) {
                        activeLines.add(lineId);
                    }
                } catch (NumberFormatException e) {
                    // ignorar filas malformadas
                }
            }
        }

        System.out.println("Lineas activas cargadas: " + activeLines.size());
        return activeLines;
    }
}