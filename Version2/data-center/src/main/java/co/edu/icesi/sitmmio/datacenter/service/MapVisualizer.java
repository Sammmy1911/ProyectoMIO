package co.edu.icesi.sitmmio.datacenter.service;

import co.edu.icesi.sitmmio.datacenter.model.BusEvent;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.WaypointPainter;
import org.jxmapviewer.viewer.DefaultWaypoint;
import org.jxmapviewer.input.PanMouseInputListener;
import org.jxmapviewer.input.ZoomMouseWheelListenerCenter;

import javax.swing.*;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MapVisualizer extends JFrame implements SITMObserver {

    private final JXMapViewer mapViewer;
    private final Map<Integer, BusState> buses = new ConcurrentHashMap<>();
    private final WaypointPainter<BusWaypoint> waypointPainter = new WaypointPainter<>();
    private int filterLineId = 0; // 0 significa ver todos

    // Clase interna para guardar el estado del bus
    private static class BusState {
        GeoPosition pos;
        int lineId;
        long lastUpdate;
        long lastTimestamp; // Tiempo real del evento en el CSV

        BusState(GeoPosition pos, int lineId, long timestamp) {
            this.pos = pos;
            this.lineId = lineId;
            this.lastTimestamp = timestamp;
            this.lastUpdate = System.currentTimeMillis();
        }
    }

    // Clase interna para guardar el estado del bus con color personalizado
    private static class BusWaypoint extends DefaultWaypoint {
        final Color color;
        final int lineId;

        BusWaypoint(GeoPosition pos, int lineId) {
            super(pos);
            this.lineId = lineId;
            int hash = String.valueOf(lineId).hashCode();
            this.color = new Color(Math.abs(hash * 123) % 200, 
                                   Math.abs(hash * 456) % 200, 
                                   Math.abs(hash * 789) % 200);
        }
    }

    public MapVisualizer() {
        setTitle("SITM-MIO: Centro de Control Cali");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // ── Panel de Control Superior ───────────────────────────────────────
        JPanel controlPanel = new JPanel();
        controlPanel.setBackground(new Color(45, 45, 45));
        JLabel label = new JLabel("Seguir Ruta (ID): ");
        label.setForeground(Color.WHITE);
        JTextField lineFilterField = new JTextField(10);
        JButton btnFilter = new JButton("Filtrar");
        JButton btnClear = new JButton("Ver Todo (Caos)");

        btnFilter.addActionListener(e -> {
            try {
                filterLineId = Integer.parseInt(lineFilterField.getText());
                buses.clear(); // Limpiar para enfocar la nueva ruta
            } catch (Exception ex) { filterLineId = 0; }
        });

        btnClear.addActionListener(e -> {
            filterLineId = 0;
            lineFilterField.setText("");
        });

        controlPanel.add(label);
        controlPanel.add(lineFilterField);
        controlPanel.add(btnFilter);
        controlPanel.add(btnClear);
        add(controlPanel, BorderLayout.NORTH);

        // ── Mapa ───────────────────────────────────────────────────────────
        mapViewer = new JXMapViewer();
        OSMTileFactoryInfo info = new OSMTileFactoryInfo("SITM-MIO-Cali", "https://tile.openstreetmap.org");
        mapViewer.setTileFactory(new DefaultTileFactory(info));

        MouseInputListener mia = new PanMouseInputListener(mapViewer);
        mapViewer.addMouseListener(mia);
        mapViewer.addMouseMotionListener(mia);
        mapViewer.addMouseWheelListener(new ZoomMouseWheelListenerCenter(mapViewer));

        mapViewer.setAddressLocation(new GeoPosition(3.4215, -76.5225));
        mapViewer.setZoom(5);

        // Renderizador de Buses
        waypointPainter.setRenderer((g, map, wp) -> {
            Point2D point = map.getTileFactory().geoToPixel(wp.getPosition(), map.getZoom());
            int x = (int) point.getX();
            int y = (int) point.getY();
            g.setColor(wp.color);
            g.fillOval(x - 6, y - 6, 12, 12);
            g.setColor(Color.WHITE);
            g.drawOval(x - 6, y - 6, 12, 12);
            g.setFont(new Font("Arial", Font.BOLD, 10));
            g.drawString(String.valueOf(wp.lineId), x + 8, y + 5);
        });

        mapViewer.setOverlayPainter(waypointPainter);
        add(mapViewer, BorderLayout.CENTER);

        new Timer(200, e -> updateWaypoints()).start();
    }

    private void updateWaypoints() {
        long now = System.currentTimeMillis();
        Set<BusWaypoint> waypoints = new HashSet<>();
        
        buses.entrySet().removeIf(entry -> (now - entry.getValue().lastUpdate) > 3000);

        for (Map.Entry<Integer, BusState> entry : buses.entrySet()) {
            BusState s = entry.getValue();
            // Aplicar filtro de ruta si está activo
            if (filterLineId == 0 || s.lineId == filterLineId) {
                waypoints.add(new BusWaypoint(s.pos, s.lineId));
            }
        }
        
        waypointPainter.setWaypoints(waypoints);
        mapViewer.repaint();
    }

    @Override
    public void onBusMoved(int busId, double lat, double lon, int lineId, long timestamp) {
        buses.compute(busId, (id, current) -> {
            if (current == null || timestamp > current.lastTimestamp) {
                return new BusState(new GeoPosition(lat, lon), lineId, timestamp);
            }
            return current;
        });
    }

    @Override
    public void onSpeedUpdated(String key, double speed) {
        // Podría usarse para cambiar colores de rutas (Mapa de calor)
    }

    public void showMap() {
        SwingUtilities.invokeLater(() -> setVisible(true));
    }
}
