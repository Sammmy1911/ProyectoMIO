package com.proyectomio.monolith;

import java.util.ArrayList;
import java.util.List;

final class Csv {
    private Csv() {
    }

    static String[] split(String line) {
        List<String> values = new ArrayList<>(8);
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString().trim());
        return values.toArray(String[]::new);
    }
}
