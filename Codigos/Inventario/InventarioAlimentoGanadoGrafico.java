import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * INVENTARIO - politica (Q, R)          Tema 4.3.2 Problemas con sistemas de inventarios
 * Ejemplo: FORRAJERIA (tienda de alimento para ganado)
 *
 *  Producto: sacos de alimento balanceado de 40 kg.
 *  Demanda diaria (sacos): 10, 15, 20, 25, 30   con prob. 0.10, 0.20, 0.40, 0.20, 0.10
 *  Tiempo de entrega del proveedor (dias): 1, 2, 3   con prob. 0.30, 0.40, 0.30
 *  Politica (Q, R): al cerrar el dia, si el inventario <= R y no hay pedido pendiente, se piden Q sacos.
 *  Si no hay existencias, la venta se pierde.
 *  Costos: pedido (flete) = $100, mantener = $0.10 por saco por dia, faltante = $5 por saco perdido.
 *
 * INTERFAZ GRAFICA (Java Swing, no necesita librerias externas). Tiene dos pestanas:
 *  1) Simulacion animada de un anio: bodega en 3D que se llena y se vacia, camion de reabastecimiento,
 *     grafica del inventario dia por dia y costos acumulados.
 *  2) Optimizacion en 3D: barras 3D con el costo anual de cada politica (Q, R). Se rotan con el mouse
 *     (arrastrar) y se acercan con la rueda. La barra dorada es la politica de menor costo.
 *
 * Compilar: javac InventarioAlimentoGanadoGrafico.java     Ejecutar: java InventarioAlimentoGanadoGrafico
 */
public class InventarioAlimentoGanadoGrafico {

    // ==================== PARAMETROS DEL EJEMPLO ====================
    static final String TITULO = "INVENTARIO (Q, R)  |  Forrajer\u00eda: sacos de alimento para ganado";
    static final int[] DEM_VALORES = {10, 15, 20, 25, 30};
    static final double[] DEM_PROBS = {0.10, 0.20, 0.40, 0.20, 0.10};
    static final int[] LT_VALORES = {1, 2, 3};
    static final double[] LT_PROBS = {0.30, 0.40, 0.30};

    static final double COSTO_PEDIDO = 100.0;
    static final double COSTO_MANTENER = 0.10;
    static final double COSTO_FALTANTE = 5.0;

    static final int DIAS = 365;
    static final int INV_INICIAL = 60;
    static final int REPLICAS = 30;               // solo para la pestana 2 (cada corrida individual sigue siendo de 365 dias)
    static final int[] Q_OPCIONES = {100, 150, 200, 250};
    static final int[] R_OPCIONES = {40, 50, 60, 70, 80};
    static final int Q_INICIAL = 200;
    static final int R_INICIAL = 50;
    static final Long SEMILLA = null;             // null = cada ejecucion distinta; pon un numero (ej. 2026L) para repetir

    static final Random rnd = (SEMILLA == null) ? new Random() : new Random(SEMILLA);

    // ==================== VARIABLES ALEATORIAS ====================
    /** Transformada inversa para una variable discreta. */
    static int discreta(int[] valores, double[] probs) {
        double u = rnd.nextDouble(), acum = 0;
        for (int i = 0; i < valores.length; i++) {
            acum += probs[i];
            if (u < acum) return valores[i];
        }
        return valores[valores.length - 1];
    }

    // ==================== COLORES ====================
    static Color mezclar(Color a, Color b, double f) {
        f = Math.max(0, Math.min(1, f));
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * f),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * f),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * f));
    }

    static Color escalar(Color c, double f) {
        return new Color(Math.max(0, Math.min(255, (int) (c.getRed() * f))),
                Math.max(0, Math.min(255, (int) (c.getGreen() * f))),
                Math.max(0, Math.min(255, (int) (c.getBlue() * f))));
    }

    static final Color VERDE = new Color(0x2E9E4F), ROJO = new Color(0xD33A2C), NARANJA = new Color(0xE39A4A),
            AZUL = new Color(0x3B7DD8), ORO = new Color(0xF2C200);

    // ==================== MODELO: un anio de inventario ====================
    static class Anio {
        int Q, R, dia, inv, diaPedido, diaLlegada, nPed, perdidas, demandaHoy, faltanteHoy;
        boolean pendiente;
        double cPed, cMant, cFalt;
        long sumaInv;
        ArrayList<Integer> historial;
        ArrayList<int[]> eventos;       // {dia, tipo}: 0 = pedido, 1 = llegada, 2 = faltante

        Anio(int Q, int R) { this.Q = Q; this.R = R; reiniciar(); }

        void reiniciar() {
            dia = 0; inv = INV_INICIAL; pendiente = false; diaPedido = -1; diaLlegada = -1;
            cPed = cMant = cFalt = 0; nPed = perdidas = 0; sumaInv = 0; demandaHoy = faltanteHoy = 0;
            historial = new ArrayList<>();
            historial.add(INV_INICIAL);
            eventos = new ArrayList<>();
        }

        boolean terminado() { return dia >= DIAS; }

        /** Simula un dia (no hace nada si ya se completo el anio). */
        void paso(boolean guardar) {
            if (terminado()) return;
            dia++;
            // 1) llega el pedido (si toca hoy)
            if (pendiente && dia == diaLlegada) {
                inv += Q;
                pendiente = false;
                if (guardar) eventos.add(new int[]{dia, 1});
            }
            // 2) demanda del dia
            int d = discreta(DEM_VALORES, DEM_PROBS);
            int vendido = Math.min(inv, d);
            int falt = d - vendido;
            inv -= vendido;
            demandaHoy = d;
            faltanteHoy = falt;
            perdidas += falt;
            cFalt += COSTO_FALTANTE * falt;
            if (guardar && falt > 0) eventos.add(new int[]{dia, 2});
            // 3) cierre del dia: costo de mantener y revision de la politica
            cMant += COSTO_MANTENER * inv;
            sumaInv += inv;
            if (!pendiente && inv <= R) {
                int lt = discreta(LT_VALORES, LT_PROBS);
                pendiente = true;
                diaPedido = dia;
                diaLlegada = dia + lt;
                nPed++;
                cPed += COSTO_PEDIDO;
                if (guardar) eventos.add(new int[]{dia, 0});
            }
            if (guardar) historial.add(inv);
        }

        double costo() { return cPed + cMant + cFalt; }
    }

    /** Promedio de REPLICAS anios simulados con la politica (Q, R): {costo, pedidos, perdidas, invProm, cPed, cMant, cFalt}. */
    static double[] promedio(int Q, int R) {
        double[] tot = new double[7];
        for (int r = 0; r < REPLICAS; r++) {
            Anio a = new Anio(Q, R);
            while (!a.terminado()) a.paso(false);
            tot[0] += a.costo(); tot[1] += a.nPed; tot[2] += a.perdidas; tot[3] += (double) a.sumaInv / DIAS;
            tot[4] += a.cPed; tot[5] += a.cMant; tot[6] += a.cFalt;
        }
        for (int i = 0; i < tot.length; i++) tot[i] /= REPLICAS;
        return tot;
    }

    // ==================== ESTADO DE LA INTERFAZ ====================
    static Anio anio = new Anio(Q_INICIAL, R_INICIAL);
    static int capacidad;
    static boolean corriendo = false;
    static double acum = 0;
    static long ultimoTick = System.nanoTime();
    // vista 3D
    static double az = -0.75, el = 0.62, esc = 88.0;
    static double[][] costos = new double[Q_OPCIONES.length][R_OPCIONES.length];
    static boolean[][] hecho = new boolean[Q_OPCIONES.length][R_OPCIONES.length];
    static double[][][] detalle = new double[Q_OPCIONES.length][R_OPCIONES.length][];
    static int[] mejor = null;
    static ArrayList<int[]> pendientes = new ArrayList<>();
    static int totalCeldas = 0;

    static JFrame ventana;
    static JTabbedPane pestanas;
    static JButton btn, btnCalc, btnUsar;
    static JSpinner spinQ, spinR;
    static JSlider slider;
    static JCheckBox chkAuto;
    static JTextArea info1, info2;
    static Lienzo1 lienzo1;
    static Lienzo3D lienzo3d;

    static int leerQ() { return (Integer) spinQ.getValue(); }
    static int leerR() { return (Integer) spinR.getValue(); }

    static void reiniciar() {
        int q = leerQ(), r = leerR();
        anio = new Anio(q, r);
        capacidad = Math.max(50, (int) Math.ceil(Math.max(INV_INICIAL, q + r) * 1.1 / 50.0) * 50);
        corriendo = false;
        acum = 0;
        btn.setText("Iniciar");
    }

    static void alternar() {
        if (anio.terminado()) reiniciar();
        corriendo = !corriendo;
        btn.setText(corriendo ? "Pausar" : "Iniciar");
        ultimoTick = System.nanoTime();
    }

    static void anioCompleto() {
        if (anio.terminado() || anio.Q != leerQ() || anio.R != leerR()) reiniciar();
        while (!anio.terminado()) anio.paso(true);
        corriendo = false;
        btn.setText("Iniciar");
    }

    // ==================== PESTANA 1: SIMULACION ANIMADA ====================
    static void caja3d(Graphics2D g, double xd, double yd, double wd, double hd, Color color, double prof) {
        int x = (int) xd, y = (int) yd, w = (int) wd, h = (int) hd, d = (int) (prof * 0.7), p = (int) prof;
        Polygon arriba = new Polygon(new int[]{x, x + p, x + w + p, x + w}, new int[]{y, y - d, y - d, y}, 4);
        Polygon lado = new Polygon(new int[]{x + w, x + w + p, x + w + p, x + w}, new int[]{y, y - d, y + h - d, y + h}, 4);
        g.setColor(escalar(color, 1.15)); g.fillPolygon(arriba);
        g.setColor(escalar(color, 0.75)); g.fillPolygon(lado);
        g.setColor(color); g.fillRect(x, y, w, h);
        g.setColor(new Color(0x444444));
        g.drawPolygon(arriba); g.drawPolygon(lado); g.drawRect(x, y, w, h);
    }

    static void centrado(Graphics2D g, String s, double cx, double y) {
        g.drawString(s, (float) (cx - g.getFontMetrics().stringWidth(s) / 2.0), (float) y);
    }

    static class Lienzo1 extends JPanel {
        Lienzo1() { setPreferredSize(new Dimension(1000, 410)); setBackground(new Color(0xF6F7F9)); }

        void camion(Graphics2D g, int x, int y) {
            g.setColor(NARANJA); g.fillRect(x, y - 24, 52, 24);
            g.setColor(new Color(0x333333)); g.drawRect(x, y - 24, 52, 24);
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            centrado(g, "Q=" + anio.Q, x + 26, y - 8);
            Polygon cab = new Polygon(new int[]{x + 52, x + 66, x + 74, x + 74, x + 52}, new int[]{y - 17, y - 17, y - 8, y, y}, 5);
            g.setColor(new Color(0xC96A1A)); g.fillPolygon(cab);
            g.setColor(new Color(0x333333)); g.drawPolygon(cab);
            g.fillOval(x + 8, y - 6, 14, 14);
            g.fillOval(x + 52, y - 6, 14, 14);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Anio a = anio;
            Font negrita = new Font("SansSerif", Font.BOLD, 13);
            Font chica = new Font("SansSerif", Font.PLAIN, 10);
            Color gris = new Color(0x999999);
            // ---- bodega en 3D ----
            int x0 = 45, ytop = 75, w = 150, H = 220, prof = 34;
            int d = (int) (prof * 0.7), ybase = ytop + H, cap = capacidad;
            g.setColor(Color.BLACK); g.setFont(negrita);
            centrado(g, "Bodega (sacos)", x0 + w / 2.0 + 15, 34);
            g.setColor(gris);
            g.drawRect(x0, ytop, w, H);
            g.drawLine(x0, ytop, x0 + prof, ytop - d);
            g.drawLine(x0 + w, ytop, x0 + w + prof, ytop - d);
            g.drawLine(x0 + prof, ytop - d, x0 + w + prof, ytop - d);
            g.drawLine(x0 + w + prof, ytop - d, x0 + w + prof, ybase - d);
            g.drawLine(x0 + w, ybase, x0 + w + prof, ybase - d);
            double nivel = H * Math.min(a.inv, cap) / (double) cap;
            if (nivel > 0) caja3d(g, x0, ybase - nivel, w, nivel, a.inv > a.R ? VERDE : NARANJA, prof);
            int yr = (int) (ybase - H * a.R / (double) cap);
            g.setColor(ROJO);
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5, 3}, 0));
            g.drawLine(x0 - 8, yr, x0 + w + prof + 8, yr);
            g.setStroke(new BasicStroke(1));
            g.setFont(negrita);
            g.drawString("R", x0 - 22, yr + 5);
            g.setColor(Color.BLACK);
            centrado(g, "Inventario: " + a.inv + " sacos", x0 + w / 2.0 + 15, ybase + 20);
            // ---- camion de reabastecimiento ----
            g.setColor(new Color(0x777777)); g.setStroke(new BasicStroke(2)); g.drawLine(30, 385, 250, 385); g.setStroke(new BasicStroke(1));
            g.setColor(Color.BLACK); g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            if (a.pendiente) {
                double prog = (a.dia - a.diaPedido) / (double) Math.max(1, a.diaLlegada - a.diaPedido);
                camion(g, (int) (40 + prog * 110), 379);
                int faltan = a.diaLlegada - a.dia;
                g.drawString("Pedido en camino: llega en " + faltan + " d\u00eda" + (faltan == 1 ? "" : "s"), x0, 344);
            } else {
                g.drawString("Sin pedido pendiente", x0, 344);
            }
            // ---- grafica del inventario ----
            int px0 = 310, py0 = 60, px1 = 975, py1 = 340;
            double ymax = cap;
            g.setColor(Color.BLACK); g.setFont(negrita);
            centrado(g, "Inventario al cierre de cada d\u00eda", (px0 + px1) / 2.0, 26);
            g.setColor(Color.WHITE); g.fillRect(px0, py0, px1 - px0, py1 - py0);
            g.setColor(new Color(0x888888)); g.drawRect(px0, py0, px1 - px0, py1 - py0);
            g.setFont(chica);
            for (int i = 0; i <= 4; i++) {
                int y = py1 - (py1 - py0) * i / 4;
                g.setColor(new Color(0xE6E6E6)); g.drawLine(px0 + 1, y, px1 - 1, y);
                g.setColor(Color.BLACK);
                String s = String.valueOf((int) (ymax * i / 4.0));
                g.drawString(s, px0 - 6 - g.getFontMetrics().stringWidth(s), y + 4);
            }
            for (int dia = 0; dia <= DIAS; dia += 50) {
                int x = (int) (px0 + (px1 - px0) * dia / (double) DIAS);
                g.setColor(new Color(0x888888)); g.drawLine(x, py1, x, py1 + 4);
                g.setColor(Color.BLACK); centrado(g, String.valueOf(dia), x, py1 + 16);
            }
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            centrado(g, "d\u00eda del a\u00f1o", (px0 + px1) / 2.0, py1 + 34);
            int yR = (int) (py1 - (py1 - py0) * a.R / ymax);
            g.setColor(ROJO);
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{6, 4}, 0));
            g.drawLine(px0, yR, px1, yR);
            g.setStroke(new BasicStroke(1));
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            g.drawString("R = " + a.R, px1 - 50, yR - 6);
            g.setColor(AZUL); g.setStroke(new BasicStroke(2));
            for (int i = 1; i < a.historial.size(); i++) {
                int xa = (int) (px0 + (px1 - px0) * (i - 1) / (double) DIAS), xb = (int) (px0 + (px1 - px0) * i / (double) DIAS);
                int ya = (int) (py1 - (py1 - py0) * Math.min(a.historial.get(i - 1), ymax) / ymax);
                int yb = (int) (py1 - (py1 - py0) * Math.min(a.historial.get(i), ymax) / ymax);
                g.drawLine(xa, ya, xb, yb);
            }
            g.setStroke(new BasicStroke(1));
            for (int[] ev : a.eventos) {
                int x = (int) (px0 + (px1 - px0) * ev[0] / (double) DIAS);
                int y = (int) (py1 - (py1 - py0) * Math.min(a.historial.get(ev[0]), ymax) / ymax);
                if (ev[1] == 0) marca(g, new Polygon(new int[]{x, x + 5, x, x - 5}, new int[]{y - 6, y, y + 6, y}, 4), NARANJA);
                else if (ev[1] == 1) marca(g, new Polygon(new int[]{x, x + 6, x - 6}, new int[]{y - 7, y + 5, y + 5}, 3), VERDE);
                else { g.setColor(ROJO); g.fillOval(x - 4, py1 - 8, 8, 8); g.setColor(new Color(0x333333)); g.drawOval(x - 4, py1 - 8, 8, 8); }
            }
            // leyenda
            int lx = px0 + 10;
            g.setFont(chica);
            marca(g, new Polygon(new int[]{lx, lx + 5, lx, lx - 5}, new int[]{44, 50, 56, 50}, 4), NARANJA);
            g.setColor(Color.BLACK); g.drawString("se hace un pedido", lx + 10, 54);
            marca(g, new Polygon(new int[]{lx + 130, lx + 136, lx + 124}, new int[]{43, 55, 55}, 3), VERDE);
            g.setColor(Color.BLACK); g.drawString("llega el pedido", lx + 142, 54);
            g.setColor(ROJO); g.fillOval(lx + 240, 46, 8, 8);
            g.setColor(Color.BLACK); g.drawString("d\u00eda con faltante (venta perdida)", lx + 254, 54);
            if (a.terminado()) {
                g.setColor(new Color(0xFFF8D6)); g.fillRect(px0 + 150, py0 + 90, px1 - px0 - 300, 60);
                g.setColor(new Color(0xB39B00)); g.drawRect(px0 + 150, py0 + 90, px1 - px0 - 300, 60);
                g.setColor(Color.BLACK);
                g.setFont(new Font("SansSerif", Font.BOLD, 16));
                centrado(g, "A\u00f1o completo", (px0 + px1) / 2.0, py0 + 116);
                g.setFont(new Font("SansSerif", Font.PLAIN, 14));
                centrado(g, String.format(Locale.US, "Costo total del a\u00f1o: $%.2f", a.costo()), (px0 + px1) / 2.0, py0 + 140);
            }
        }

        void marca(Graphics2D g, Polygon p, Color c) {
            g.setColor(c); g.fillPolygon(p);
            g.setColor(new Color(0x333333)); g.drawPolygon(p);
        }
    }

    static void actualizarInfo1() {
        Anio a = anio;
        double prom = a.dia > 0 ? (double) a.sumaInv / a.dia : 0;
        info1.setText(String.format(Locale.US,
                "D\u00eda %3d / %d   Pol\u00edtica (Q=%d, R=%d)   Demanda de hoy: %d sacos   Faltante de hoy: %d%n"
                        + "Pedidos: $%9.2f (%d pedidos)   Mantener: $%9.2f (inv. prom. %.1f)   Faltantes: $%9.2f (%d sacos perdidos)%n"
                        + "COSTO TOTAL ACUMULADO: $%.2f",
                a.dia, DIAS, a.Q, a.R, a.demandaHoy, a.faltanteHoy, a.cPed, a.nPed, a.cMant, prom, a.cFalt, a.perdidas, a.costo()));
    }

    // ==================== PESTANA 2: OPTIMIZACION EN 3D ====================
    static void calcularMalla() {
        hecho = new boolean[Q_OPCIONES.length][R_OPCIONES.length];
        detalle = new double[Q_OPCIONES.length][R_OPCIONES.length][];
        mejor = null;
        pendientes = new ArrayList<>();
        for (int i = 0; i < Q_OPCIONES.length; i++) for (int j = 0; j < R_OPCIONES.length; j++) pendientes.add(new int[]{i, j});
        totalCeldas = pendientes.size();
        btnCalc.setEnabled(false);
        btnUsar.setEnabled(false);
        javax.swing.Timer t = new javax.swing.Timer(5, null);
        t.addActionListener(e -> {
            if (pendientes.isEmpty()) {
                t.stop();
                btnCalc.setEnabled(true);
                btnUsar.setEnabled(true);
                double[] r = detalle[mejor[0]][mejor[1]];
                info2.setText(String.format(Locale.US,
                        "Mejor pol\u00edtica: Q = %d sacos, R = %d sacos   ->   costo anual promedio $%.2f%n"
                                + "Pedidos $%.2f (%.1f pedidos/a\u00f1o)   Mantener $%.2f (inv. prom. %.1f)   Faltantes $%.2f (%.1f sacos perdidos/a\u00f1o)%n"
                                + "Arrastra con el mouse para rotar; rueda para acercar. Altura: relativa al costo m\u00ednimo y m\u00e1ximo.",
                        Q_OPCIONES[mejor[0]], R_OPCIONES[mejor[1]], r[0], r[4], r[1], r[5], r[3], r[6], r[2]));
                return;
            }
            int[] ij = pendientes.remove(0);
            double[] r = promedio(Q_OPCIONES[ij[0]], R_OPCIONES[ij[1]]);
            costos[ij[0]][ij[1]] = r[0];
            detalle[ij[0]][ij[1]] = r;
            hecho[ij[0]][ij[1]] = true;
            if (mejor == null || r[0] < costos[mejor[0]][mejor[1]]) mejor = new int[]{ij[0], ij[1]};
            info2.setText("Calculando... " + (totalCeldas - pendientes.size()) + " de " + totalCeldas + " pol\u00edticas");
        });
        t.start();
    }

    static void usarMejor() {
        if (mejor == null) return;
        spinQ.setValue(Q_OPCIONES[mejor[0]]);
        spinR.setValue(R_OPCIONES[mejor[1]]);
        reiniciar();
        pestanas.setSelectedIndex(0);
    }

    static class Cara {
        double[][] pts;
        double factor, prof;
    }

    static class Barra {
        double prof, x, y, h, costo;
        Color color;
    }

    static class Lienzo3D extends JPanel {
        int arrastreX = -1, arrastreY = -1;

        Lienzo3D() {
            setPreferredSize(new Dimension(1000, 470));
            setBackground(Color.WHITE);
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { arrastreX = e.getX(); arrastreY = e.getY(); }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseDragged(MouseEvent e) {
                    az += (e.getX() - arrastreX) * 0.01;
                    el = Math.max(0.08, Math.min(1.5, el + (e.getY() - arrastreY) * 0.01));
                    arrastreX = e.getX();
                    arrastreY = e.getY();
                }
            });
            addMouseWheelListener(e -> esc = Math.max(40.0, Math.min(200.0, esc * (e.getWheelRotation() < 0 ? 1.08 : 0.92))));
        }

        double[] proyectar(double x, double y, double z) {
            double ca = Math.cos(az), sa = Math.sin(az), ce = Math.cos(el), se = Math.sin(el);
            double xr = x * ca - y * sa, yr = x * sa + y * ca;
            return new double[]{500 + xr * esc, 345 - (z * ce + yr * se) * esc, yr * ce - z * se};
        }

        void poligono(Graphics2D g, double[][] puntos, Color relleno, Color borde, float ancho) {
            Polygon p = new Polygon();
            for (double[] q : puntos) {
                double[] s = proyectar(q[0], q[1], q[2]);
                p.addPoint((int) s[0], (int) s[1]);
            }
            g.setColor(relleno); g.fillPolygon(p);
            g.setColor(borde); g.setStroke(new BasicStroke(ancho)); g.drawPolygon(p); g.setStroke(new BasicStroke(1));
        }

        void texto3d(Graphics2D g, double x, double y, double z, String s) {
            double[] p = proyectar(x, y, z);
            centrado(g, s, p[0], p[1] + 4);
        }

        void linea3d(Graphics2D g, double[] a, double[] b) {
            g.drawLine((int) a[0], (int) a[1], (int) b[0], (int) b[1]);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int nq = Q_OPCIONES.length, nr = R_OPCIONES.length;
            double X = (nq - 1) / 2.0 + 0.7, Y = (nr - 1) / 2.0 + 0.7;
            poligono(g, new double[][]{{-X, -Y, 0}, {X, -Y, 0}, {X, Y, 0}, {-X, Y, 0}}, new Color(0xEEF1F4), new Color(0xAAAABB), 1f);
            g.setColor(new Color(0xD5DAE0));
            for (int i = 0; i <= nq; i++) {
                double x = -X + 0.2 + i * (2 * X - 0.4) / nq;
                linea3d(g, proyectar(x, -Y, 0), proyectar(x, Y, 0));
            }
            for (int j = 0; j <= nr; j++) {
                double y = -Y + 0.2 + j * (2 * Y - 0.4) / nr;
                linea3d(g, proyectar(-X, y, 0), proyectar(X, y, 0));
            }
            double[] a = proyectar(-X, -Y, 0), b = proyectar(-X, -Y, 2.7);
            g.setColor(new Color(0x666666)); g.setStroke(new BasicStroke(2)); linea3d(g, a, b); g.setStroke(new BasicStroke(1));
            g.setColor(Color.BLACK); g.setFont(new Font("SansSerif", Font.BOLD, 12));
            centrado(g, "costo anual ($)", b[0], b[1] - 10);
            g.setColor(new Color(0x334455)); g.setFont(new Font("SansSerif", Font.BOLD, 13));
            texto3d(g, 0, -Y - 0.75, 0, "Q = sacos por pedido");
            texto3d(g, -X - 0.95, 0, 0, "R = punto de reorden");
            g.setColor(Color.BLACK); g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            for (int i = 0; i < nq; i++) texto3d(g, i - (nq - 1) / 2.0, -Y - 0.3, 0, String.valueOf(Q_OPCIONES[i]));
            for (int j = 0; j < nr; j++) texto3d(g, -X - 0.3, j - (nr - 1) / 2.0, 0, String.valueOf(R_OPCIONES[j]));

            double cmin = Double.MAX_VALUE, cmax = -Double.MAX_VALUE;
            for (int i = 0; i < nq; i++) for (int j = 0; j < nr; j++) if (hecho[i][j]) { cmin = Math.min(cmin, costos[i][j]); cmax = Math.max(cmax, costos[i][j]); }
            ArrayList<Barra> barras = new ArrayList<>();
            for (int i = 0; i < nq; i++) {
                for (int j = 0; j < nr; j++) {
                    Barra br = new Barra();
                    br.x = i - (nq - 1) / 2.0;
                    br.y = j - (nr - 1) / 2.0;
                    if (hecho[i][j]) {
                        double t = cmax > cmin ? (costos[i][j] - cmin) / (cmax - cmin) : 0;
                        br.h = 0.35 + 1.85 * t;
                        br.costo = costos[i][j];
                        br.color = (mejor != null && mejor[0] == i && mejor[1] == j && pendientes.isEmpty()) ? ORO : mezclar(VERDE, ROJO, t);
                    } else {
                        br.h = 0.03;
                        br.costo = -1;
                        br.color = new Color(0xDFE3E8);
                    }
                    br.prof = proyectar(br.x, br.y, 0)[2];
                    barras.add(br);
                }
            }
            barras.sort((p, q) -> Double.compare(q.prof, p.prof));
            for (Barra br : barras) {
                double bb = 0.36, x0 = br.x - bb, x1 = br.x + bb, y0 = br.y - bb, y1 = br.y + bb, h = br.h;
                double[][][] caras = {
                        {{x0, y0, h}, {x1, y0, h}, {x1, y1, h}, {x0, y1, h}},
                        {{x0, y0, 0}, {x1, y0, 0}, {x1, y0, h}, {x0, y0, h}},
                        {{x0, y1, 0}, {x1, y1, 0}, {x1, y1, h}, {x0, y1, h}},
                        {{x0, y0, 0}, {x0, y1, 0}, {x0, y1, h}, {x0, y0, h}},
                        {{x1, y0, 0}, {x1, y1, 0}, {x1, y1, h}, {x1, y0, h}}};
                double[] factores = {1.10, 0.95, 0.75, 0.85, 0.70};
                ArrayList<Cara> lista = new ArrayList<>();
                for (int c = 0; c < 5; c++) {
                    Cara cr = new Cara();
                    cr.pts = caras[c];
                    cr.factor = factores[c];
                    for (double[] p : caras[c]) cr.prof += proyectar(p[0], p[1], p[2])[2];
                    lista.add(cr);
                }
                lista.sort((p, q) -> Double.compare(q.prof, p.prof));
                boolean oro = br.color.equals(ORO);
                for (Cara cr : lista) poligono(g, cr.pts, escalar(br.color, cr.factor), oro ? new Color(0x333333) : new Color(0x555555), oro ? 2f : 1f);
                if (br.costo >= 0) {
                    g.setColor(new Color(0x222222)); g.setFont(new Font("SansSerif", Font.BOLD, 11));
                    texto3d(g, br.x, br.y, h + 0.14, String.format(Locale.US, "$%,d", Math.round(br.costo)));
                }
            }
            g.setColor(new Color(0x555555)); g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.drawString("Arrastra para rotar  |  rueda del mouse: zoom  |  verde = menor costo, rojo = mayor, dorado = mejor pol\u00edtica", 20, 20);
        }
    }

    // ==================== CICLO DE ANIMACION ====================
    static void tick() {
        long ahora = System.nanoTime();
        double dt = Math.min((ahora - ultimoTick) / 1e9, 0.1);
        ultimoTick = ahora;
        int pestana = pestanas.getSelectedIndex();
        if (corriendo && !anio.terminado()) {
            acum += dt * slider.getValue();
            while (acum >= 1 && !anio.terminado()) {
                anio.paso(true);
                acum -= 1;
            }
            if (anio.terminado()) { corriendo = false; btn.setText("Iniciar"); }
        }
        if (pestana == 0) {
            actualizarInfo1();
            lienzo1.repaint();
        } else {
            if (chkAuto.isSelected()) az += 0.012;
            lienzo3d.repaint();
        }
    }

    static void crearVentana() {
        ventana = new JFrame(TITULO);
        ventana.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pestanas = new JTabbedPane();

        // ---- pestana 1 ----
        JPanel t1 = new JPanel(new BorderLayout());
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        btn = new JButton("Iniciar");
        btn.addActionListener(e -> alternar());
        JButton bRein = new JButton("Reiniciar");
        bRein.addActionListener(e -> reiniciar());
        JButton bAnio = new JButton("A\u00f1o completo (instant\u00e1neo)");
        bAnio.addActionListener(e -> anioCompleto());
        spinQ = new JSpinner(new SpinnerNumberModel(Q_INICIAL, 50, 400, 10));
        spinR = new JSpinner(new SpinnerNumberModel(R_INICIAL, 0, 200, 5));
        spinQ.addChangeListener(e -> reiniciar());
        spinR.addChangeListener(e -> reiniciar());
        slider = new JSlider(1, 120, 25);
        slider.setPreferredSize(new Dimension(120, 40));
        JLabel valorVel = new JLabel("25");
        slider.addChangeListener(e -> valorVel.setText(String.valueOf(slider.getValue())));
        barra.add(btn); barra.add(bRein); barra.add(bAnio);
        barra.add(new JLabel("  Q (sacos por pedido):")); barra.add(spinQ);
        barra.add(new JLabel("  R (punto de reorden):")); barra.add(spinR);
        barra.add(new JLabel("  Velocidad (d\u00edas/seg):")); barra.add(slider); barra.add(valorVel);
        lienzo1 = new Lienzo1();
        info1 = new JTextArea(3, 100);
        info1.setEditable(false);
        info1.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        info1.setMargin(new Insets(6, 10, 6, 10));
        t1.add(barra, BorderLayout.NORTH);
        t1.add(lienzo1, BorderLayout.CENTER);
        t1.add(info1, BorderLayout.SOUTH);

        // ---- pestana 2 ----
        JPanel t2 = new JPanel(new BorderLayout());
        JPanel barra2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        btnCalc = new JButton("Calcular malla (" + Q_OPCIONES.length + " x " + R_OPCIONES.length + " pol\u00edticas, " + REPLICAS + " a\u00f1os c/u)");
        btnCalc.addActionListener(e -> calcularMalla());
        chkAuto = new JCheckBox("Rotar autom\u00e1ticamente", true);
        btnUsar = new JButton("Usar la mejor pol\u00edtica en la simulaci\u00f3n animada");
        btnUsar.setEnabled(false);
        btnUsar.addActionListener(e -> usarMejor());
        barra2.add(btnCalc); barra2.add(chkAuto); barra2.add(btnUsar);
        lienzo3d = new Lienzo3D();
        info2 = new JTextArea(3, 100);
        info2.setEditable(false);
        info2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        info2.setMargin(new Insets(6, 10, 6, 10));
        info2.setText("Pulsa \"Calcular malla\" para simular cada pol\u00edtica (Q, R) y ver su costo anual en 3D.");
        t2.add(barra2, BorderLayout.NORTH);
        t2.add(lienzo3d, BorderLayout.CENTER);
        t2.add(info2, BorderLayout.SOUTH);

        pestanas.addTab("  1) Simulaci\u00f3n de un a\u00f1o (animada)  ", t1);
        pestanas.addTab("  2) Optimizaci\u00f3n 3D: costo seg\u00fan Q y R  ", t2);
        ventana.add(pestanas);
        reiniciar();
        ventana.pack();
        ventana.setResizable(false);
        ventana.setLocationRelativeTo(null);
        ventana.setVisible(true);
        new javax.swing.Timer(30, e -> tick()).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(InventarioAlimentoGanadoGrafico::crearVentana);
    }
}
