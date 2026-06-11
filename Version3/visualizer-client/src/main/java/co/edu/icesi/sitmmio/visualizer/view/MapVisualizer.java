package co.edu.icesi.sitmmio.visualizer.view;

import co.edu.icesi.sitmmio.visualizer.model.BusModel;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.viewer.*;
import org.jxmapviewer.input.PanMouseInputListener;
import org.jxmapviewer.input.ZoomMouseWheelListenerCenter;

import javax.swing.*;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MapVisualizer {

    private final JXMapViewer mapViewer;
    private final BusModel model;
    private final JFrame frame;
    private final WaypointPainter<BusWaypoint> waypointPainter = new WaypointPainter<>();
    private int filterLineId = 0;

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

    public MapVisualizer(BusModel model) {
        this.model = model;
        this.mapViewer = new JXMapViewer();
        this.frame = new JFrame("SITM-MIO: Centro de Control Cali (Distribuido)");
        setupUI();
    }

    private void setupUI() {
        frame.setSize(1200, 800);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // ── Panel de Control Superior ───────────────────────────────────────
        JPanel controlPanel = new JPanel();
        controlPanel.setBackground(new Color(45, 45, 45));
        JLabel label = new JLabel("Seguir Ruta (ID): ");
        label.setForeground(Color.WHITE);
        JTextField lineFilterField = new JTextField(10);
        JButton btnFilter = new JButton("Filtrar");
        JButton btnClear = new JButton("Ver Todo");

        btnFilter.addActionListener(e -> {
            try {
                filterLineId = Integer.parseInt(lineFilterField.getText());
                // No limpiamos el modelo aquí para evitar ConcurrentModificationException, 
                // el timer se encargará de filtrar en el siguiente ciclo.
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
        frame.add(controlPanel, BorderLayout.NORTH);

        // ── Mapa ───────────────────────────────────────────────────────────
        OSMTileFactoryInfo info = new OSMTileFactoryInfo("SITM-MIO-Cali", "https://tile.openstreetmap.org");
        mapViewer.setTileFactory(new DefaultTileFactory(info));

        MouseInputListener mia = new PanMouseInputListener(mapViewer);
        mapViewer.addMouseListener(mia);
        mapViewer.addMouseMotionListener(mia);
        mapViewer.addMouseWheelListener(new ZoomMouseWheelListenerCenter(mapViewer));

        // Centro exacto en Cali (Terminal de Transportes / Centro)
        mapViewer.setAddressLocation(new GeoPosition(3.4215, -76.5225));
        mapViewer.setZoom(5);

        // Renderizador de Waypoints (Buses)
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
        frame.add(mapViewer, BorderLayout.CENTER);

        // Timer para actualizar waypoints a partir del modelo
        new Timer(200, e -> updateWaypoints()).start();
    }

    private void updateWaypoints() {
        Set<BusWaypoint> waypoints = new HashSet<>();
        Map<Integer, BusModel.BusState> positions = model.getBusPositions();
        long now = System.currentTimeMillis();

        // Limpiar buses viejos del modelo (3 segundos sin recibir datos)
        positions.entrySet().removeIf(entry -> (now - entry.getValue().lastUpdate) > 3000);

        for (BusModel.BusState s : positions.values()) {
            if (filterLineId == 0 || s.lineId == filterLineId) {
                waypoints.add(new BusWaypoint(new GeoPosition(s.lat, s.lon), s.lineId));
            }
        }
        waypointPainter.setWaypoints(waypoints);
        mapViewer.repaint();
    }

    public void show() {
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
    }
}
