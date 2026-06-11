package co.edu.icesi.sitmmio.simulator;

import java.io.*;
import java.util.*;

public class CsvLoader {

    private final RandomAccessFile raf;
    private boolean finished = false;
    private final long fileLength;

    public CsvLoader(String filePath) throws IOException {
        File file = new File(filePath);
        this.raf = new RandomAccessFile(file, "r");
        this.fileLength = file.length();
    }

    /**
     * Lee un bloque de bytes asegurando que no corte una línea por la mitad.
     */
    public byte[] nextByteChunk(int targetSize) throws IOException {
        if (finished) return new byte[0];

        long currentPos = raf.getFilePointer();
        int toRead = (int) Math.min(targetSize, fileLength - currentPos);
        
        if (toRead <= 0) {
            finished = true;
            return new byte[0];
        }

        byte[] buffer = new byte[toRead];
        raf.readFully(buffer);

        // Buscar el último salto de línea para no dejar líneas a medias
        int lastNewLine = -1;
        for (int i = buffer.length - 1; i >= 0; i--) {
            if (buffer[i] == '\n') {
                lastNewLine = i;
                break;
            }
        }

        if (lastNewLine == -1) {
            // Caso borde: bloque sin saltos de línea, leemos hasta encontrar uno
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(buffer);
            int b;
            while ((b = raf.read()) != -1) {
                baos.write(b);
                if (b == '\n') break;
            }
            if (b == -1) finished = true;
            return baos.toByteArray();
        } else {
            // Retroceder el puntero del archivo hasta el final de la última línea completa
            raf.seek(currentPos + lastNewLine + 1);
            byte[] result = new byte[lastNewLine + 1];
            System.arraycopy(buffer, 0, result, 0, result.length);
            return result;
        }
    }

    public boolean isFinished() { return finished; }
    public void close() throws IOException { raf.close(); }
}
