package co.edu.icesi.sitmmio.datacenter.repository;

import co.edu.icesi.sitmmio.datacenter.model.BusEvent;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;

public class CsvLoader {

    private static final int IDX_STOP_ID       = 2;
    private static final int IDX_LATITUDE      = 4;
    private static final int IDX_LONGITUDE     = 5;
    private static final int IDX_LINE_ID       = 7;
    private static final int IDX_TRIP_ID       = 8;
    private static final int IDX_DATAGRAM_DATE = 10;
    private static final int IDX_BUS_ID        = 11;

    private final BufferedReader reader;
    private boolean finished = false;
    private int totalLoaded = 0;

    private final int[] commaPos = new int[32];

    public CsvLoader(String filePath) throws IOException {
        this.reader = new BufferedReader(new FileReader(filePath), 1024 * 1024); // buffer 1MB
    }

    public List<BusEvent> nextChunk(int chunkSize, Set<Integer> activeLineIds) throws IOException {
        List<BusEvent> chunk = new ArrayList<>(chunkSize);
        if (finished) return chunk;

        String line;
        while (chunk.size() < chunkSize && (line = reader.readLine()) != null) {
            if (line.isEmpty()) continue;

            int commaCount = 0;
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == ',') {
                    commaPos[commaCount++] = i;
                }
            }

            if (commaCount < 11) continue;

            try {
                int rawLat = parseFastInt(line, commaPos[IDX_LATITUDE - 1] + 1, commaPos[IDX_LATITUDE]);
                int rawLon = parseFastInt(line, commaPos[IDX_LONGITUDE - 1] + 1, commaPos[IDX_LONGITUDE]);
                int lineId = parseFastInt(line, commaPos[IDX_LINE_ID - 1] + 1, commaPos[IDX_LINE_ID]);
                int tripId = parseFastInt(line, commaPos[IDX_TRIP_ID - 1] + 1, commaPos[IDX_TRIP_ID]);
                int stopId = parseFastInt(line, commaPos[IDX_STOP_ID - 1] + 1, commaPos[IDX_STOP_ID]);
                int busId  = parseFastInt(line, commaPos[IDX_BUS_ID - 1] + 1, line.length());

                if (rawLat == -1 || rawLon == -1)    continue;
                if (lineId == -1)                    continue;
                if (!activeLineIds.contains(lineId)) continue;

                double latitude  = rawLat / 1e7;
                double longitude = rawLon / 1e7;

                LocalDateTime date = parseFastDate(line, commaPos[IDX_DATAGRAM_DATE - 1] + 1, commaPos[IDX_DATAGRAM_DATE]);

                chunk.add(new BusEvent(busId, lineId, tripId, stopId,
                                       latitude, longitude, date));

            } catch (Exception e) {
                // ignorar línea malformada
            }
        }

        if (chunk.isEmpty()) {
            finished = true;
        }

        totalLoaded += chunk.size();
        return chunk;
    }

    private int parseFastInt(String s, int start, int end) {
        int res = 0;
        boolean neg = false;
        int i = start;
        while (i < end && s.charAt(i) == ' ') i++;
        if (i < end && s.charAt(i) == '-') {
            neg = true;
            i++;
        }
        for (; i < end; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') break;
            res = res * 10 + (c - '0');
        }
        return neg ? -res : res;
    }

    private LocalDateTime parseFastDate(String s, int start, int end) {
        // Formato: 2018-05-31 00:00:00
        while (start < end && s.charAt(start) == ' ') start++;
        int y = parseFastInt(s, start,      start + 4);
        int m = parseFastInt(s, start + 5,  start + 7);
        int d = parseFastInt(s, start + 8,  start + 10);
        int h = parseFastInt(s, start + 11, start + 13);
        int i = parseFastInt(s, start + 14, start + 16);
        int x = parseFastInt(s, start + 17, start + 19);
        return LocalDateTime.of(y, m, d, h, i, x);
    }

    public boolean isFinished() { return finished; }
    public int getTotalLoaded() { return totalLoaded; }

    public void close() throws IOException {
        reader.close();
    }
}