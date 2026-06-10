package co.edu.icesi.sitmmio.datacenter.repository;

import java.io.*;
import java.util.*;

public class CsvLoader {

    private final BufferedReader reader;
    private boolean finished = false;

    public CsvLoader(String filePath) throws IOException {
        this.reader = new BufferedReader(new FileReader(filePath), 1024 * 1024); // Buffer 1MB
    }

    public List<String> nextRawChunk(int chunkSize) throws IOException {
        List<String> chunk = new ArrayList<>(chunkSize);
        if (finished) return chunk;

        String line;
        while (chunk.size() < chunkSize && (line = reader.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty()) {
                chunk.add(line);
            }
        }

        if (chunk.isEmpty()) finished = true;
        return chunk;
    }

    public boolean isFinished() { return finished; }
    public void close() throws IOException { reader.close(); }
}