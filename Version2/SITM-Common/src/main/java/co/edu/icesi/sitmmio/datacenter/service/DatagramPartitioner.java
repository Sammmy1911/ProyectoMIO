package co.edu.icesi.sitmmio.datacenter.service;

public class DatagramPartitioner {

    private static final int IDX_TRIP_ID = 8;
    private static final int IDX_BUS_ID = 11;

    public int partitionForRawLine(String line, int partitions) {
        if (partitions <= 0) {
            throw new IllegalArgumentException("partitions must be greater than zero");
        }

        int busId = 0;
        int tripId = 0;
        int column = 0;
        int start = 0;

        for (int i = 0; i <= line.length(); i++) {
            if (i == line.length() || line.charAt(i) == ',') {
                if (column == IDX_TRIP_ID) {
                    tripId = fastInt(line, start, i);
                } else if (column == IDX_BUS_ID) {
                    busId = fastInt(line, start, i);
                    break;
                }
                start = i + 1;
                column++;
            }
        }

        int hash = 31 * busId + tripId;
        return Math.floorMod(hash, partitions);
    }

    private int fastInt(String s, int start, int end) {
        int res = 0;
        int i = start;
        boolean neg = false;
        if (i < end && s.charAt(i) == '-') {
            neg = true;
            i++;
        }
        if (i >= end) {
            return 0;
        }
        for (; i < end; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            res = res * 10 + (c - '0');
        }
        return neg ? -res : res;
    }
}
