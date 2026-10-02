/*
================================================================================
LÍNEA DE ESPERA 2 - MULTISERVIDOR (M/M/c)
Tema 4.3.1 - Problemas con líneas de espera
Ejemplo: MUELLES DE DESCARGA de un puerto pesquero
(Versión Java / Swing, adaptada de linea_espera_2_puerto_grafico.py)
================================================================================

DESCRIPCIÓN DEL SISTEMA
-----------------------
- Llegan en promedio 1 barco por hora (tiempos entre llegadas exponenciales).
- Cada muelle tarda en promedio 2.5 horas en descargar un barco
  (tasa de servicio mu = 0.4 barcos/hora por muelle).
- Una sola fila; el barco ocupa el primer muelle que se desocupe (FIFO).
- Sistema M/M/c (llegadas Markovianas, servicio Markoviano, c servidores).

CONDICIÓN DE TERMINACIÓN
------------------------
La simulación se detiene automáticamente cuando se cumplen las tres condiciones:
  1. Se han atendido al menos MAX_ATENDIDOS barcos (por defecto 800).
  2. No queda nadie en la fila de espera.
  3. Todos los muelles están libres.

CÓMO EJECUTAR
-------------
    javac -encoding UTF-8 LineaEspera2PuertoGrafico.java
    java LineaEspera2PuertoGrafico

  (o directamente, con Java 11+:  java LineaEspera2PuertoGrafico.java)
*/

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class LineaEspera2PuertoGrafico {

    // =========================================================================
    // CONFIGURACIÓN DEL EJEMPLO
    // =========================================================================
    static final String TITULO = "LÍNEA DE ESPERA 2 - MULTISERVIDOR (M/M/c)  |  Muelles de un puerto pesquero";
    static final String NOMBRE_CLIENTES = "barcos";
    static final String NOMBRE_SERVIDOR = "Muelle";
    static final String UNIDAD = "horas";

    // Parámetros del sistema M/M/c
    static final double TASA_LLEGADA = 1.0;     // lambda = barcos por hora
    static final double TASA_SERVICIO = 0.4;    // mu por muelle = barcos por hora (2.5 h por barco)

    // Configuración de la simulación
    static final int SERVIDORES_INICIAL = 3;
    static final boolean SERVIDORES_EDITABLES = true;
    static final double VELOCIDAD_INICIAL = 3.0;    // horas simuladas por segundo real
    static final int MAX_ATENDIDOS = 800;           // número mínimo de barcos que deben ser atendidos
    static final Long SEMILLA = null;               // null = cada ejecución distinta; pon un número (ej. 7L) para repetir

    // Apariencia
    static final String FORMA_CLIENTE = "barco";    // "persona" o "barco"
    static final Color COLOR_FONDO = Color.decode("#cfe8f7");
    static final Color COLOR_CARRIL = Color.decode("#b7d9ee");
    static final Color COLOR_SERV_LIBRE = Color.decode("#c9a878");
    static final Color COLOR_SERV_OCUPADO = Color.decode("#e39a4a");

    // Geometría de la animación
    static final int ANCHO = 1000;
    static final int ALTO = 330;
    static final int X_ENTRADA = 45;
    static final int X_COLA_CABEZA = 470;
    static final int SEP = FORMA_CLIENTE.equals("barco") ? 40 : 28;
    static final int MAX_VISIBLES = (int) ((X_COLA_CABEZA - (X_ENTRADA + 30)) / (double) SEP);
    static final int SERV_X = 620;
    static final int CAJA_ANCHO = 160;
    static final Color VERDE = Color.decode("#2e9e4f");
    static final Color ROJO = Color.decode("#d33a2c");
    static final Color AZUL = Color.decode("#3b7dd8");

    static Random RND = new Random();

    // =========================================================================
    // GENERADORES DE VARIABLES ALEATORIAS
    // =========================================================================

    /** Exponencial por transformada inversa: X = -ln(1 - U) / tasa, U ~ Uniforme(0,1). */
    static double exponencial(double tasa) {
        return -Math.log(1.0 - RND.nextDouble()) / tasa;
    }

    /** Medidas teóricas de un sistema M/M/c. */
    static class Teoria {
        double wq, w, lq, rho;
        double get(String k) {
            switch (k) {
                case "Wq": return wq;
                case "W":  return w;
                case "Lq": return lq;
                default:   return rho;
            }
        }
    }

    static double factorial(int n) {
        double f = 1.0;
        for (int i = 2; i <= n; i++) f *= i;
        return f;
    }

    /**
     * Calcula las medidas de desempeño teóricas de un sistema M/M/c (Erlang C).
     * Para c = 1 se reduce a las fórmulas clásicas de M/M/1.
     * Retorna null si el sistema es inestable (rho >= 1).
     */
    static Teoria erlangC(int c, double tasaLl, double tasaSv) {
        double a = tasaLl / tasaSv;
        double rho = a / c;
        if (rho >= 1) return null;

        double suma = 0.0;
        for (int k = 0; k < c; k++) suma += Math.pow(a, k) / factorial(k);
        double ultimo = Math.pow(a, c) / (factorial(c) * (1 - rho));
        double probEsperar = ultimo / (suma + ultimo);

        Teoria t = new Teoria();
        t.lq = probEsperar * rho / (1 - rho);
        t.wq = t.lq / tasaLl;
        t.w = t.wq + 1 / tasaSv;
        t.rho = rho;
        return t;
    }

    // =========================================================================
    // UTILIDADES DE COLOR Y TEXTO
    // =========================================================================
    static int limitar(double v) {
        return Math.max(0, Math.min(255, (int) v));
    }

    /** Mezcla linealmente dos colores. f=0 -> c1, f=1 -> c2. */
    static Color mezclar(Color c1, Color c2, double f) {
        return new Color(
                limitar(c1.getRed() + (c2.getRed() - c1.getRed()) * f),
                limitar(c1.getGreen() + (c2.getGreen() - c1.getGreen()) * f),
                limitar(c1.getBlue() + (c2.getBlue() - c1.getBlue()) * f));
    }

    /** Oscurece o aclara un color multiplicando sus componentes por factor. */
    static Color escalar(Color c, double factor) {
        return new Color(limitar(c.getRed() * factor),
                limitar(c.getGreen() * factor),
                limitar(c.getBlue() * factor));
    }

    static boolean fuenteExiste(String nombre) {
        for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
            if (f.equalsIgnoreCase(nombre)) return true;
        return false;
    }

    static final String FAMILIA = fuenteExiste("Segoe UI") ? "Segoe UI" : Font.SANS_SERIF;
    static final String FAMILIA_MONO = fuenteExiste("Consolas") ? "Consolas" : Font.MONOSPACED;

    /** Fuente con tamaño en "puntos tk" (se escala a píxeles de Java2D). */
    static Font fuente(int estilo, int pt) {
        return new Font(FAMILIA, estilo, Math.round(pt * 1.33f));
    }

    /** Dibuja texto con el anclaje de tkinter: "w" (izq.), "e" (der.) o centrado. */
    static void texto(Graphics2D g, String s, double x, double y, String anchor, Font f, Color col) {
        g.setFont(f);
        g.setColor(col);
        FontMetrics fm = g.getFontMetrics();
        double w = fm.stringWidth(s);
        double px = anchor.equals("w") ? x : anchor.equals("e") ? x - w : x - w / 2;
        double py = y + (fm.getAscent() - fm.getDescent()) / 2.0;
        g.drawString(s, (float) px, (float) py);
    }

    static void poligono(Graphics2D g, Color relleno, Color borde, double... p) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(p[0], p[1]);
        for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
        path.closePath();
        g.setColor(relleno);
        g.fill(path);
        g.setColor(borde);
        g.draw(path);
    }

    // =========================================================================
    // MODELO DE SIMULACIÓN DE EVENTOS DISCRETOS
    // =========================================================================

    /** Representa a un barco individual. */
    static class Cliente {
        double llegada;                 // instante de llegada al sistema
        double servicio;                // duración del servicio
        double x = Double.NaN;          // posición horizontal en pantalla (NaN = aún sin posición)
        double y = Double.NaN;          // posición vertical en pantalla

        Cliente(double llegada, double servicio) {
            this.llegada = llegada;
            this.servicio = servicio;
        }
    }

    /**
     * Modelo de simulación de eventos discretos para una cola M/M/c.
     * Eventos: llegada de un barco y fin de servicio.
     */
    static class Modelo {
        int c;
        double t, ultimo, sigLlegada;
        Cliente[] servidores;
        double[] fin;
        List<Cliente> cola;
        List<Cliente> salieron;
        int nLlegadas, nInicio, nSalidas;
        double sumaEspera, sumaServicio, areaCola, areaOcupados;
        ArrayDeque<double[]> historial;     // {t, largo de cola, wq}, máx. 3000

        Modelo(int c) {
            this.c = c;
            reiniciar();
        }

        void reiniciar() {
            t = 0.0;
            ultimo = 0.0;
            sigLlegada = exponencial(TASA_LLEGADA);
            servidores = new Cliente[c];
            fin = new double[c];
            java.util.Arrays.fill(fin, Double.POSITIVE_INFINITY);
            cola = new ArrayList<>();
            salieron = new ArrayList<>();
            nLlegadas = nInicio = nSalidas = 0;
            sumaEspera = sumaServicio = areaCola = areaOcupados = 0.0;
            historial = new ArrayDeque<>();
        }

        int ocupados() {
            int n = 0;
            for (Cliente s : servidores) if (s != null) n++;
            return n;
        }

        private void acumular(double tt) {
            double dt = tt - ultimo;
            areaCola += cola.size() * dt;
            areaOcupados += ocupados() * dt;
            ultimo = tt;
        }

        private void iniciar(Cliente cli, int k, double tt) {
            sumaEspera += tt - cli.llegada;
            sumaServicio += cli.servicio;
            nInicio++;
            servidores[k] = cli;
            fin[k] = tt + cli.servicio;
        }

        private void llegada() {
            double tt = sigLlegada;
            Cliente cli = new Cliente(tt, exponencial(TASA_SERVICIO));
            nLlegadas++;

            int libre = -1;
            for (int k = 0; k < c; k++) {
                if (servidores[k] == null) { libre = k; break; }
            }
            if (libre >= 0) iniciar(cli, libre, tt);
            else cola.add(cli);

            sigLlegada = tt + exponencial(TASA_LLEGADA);
        }

        private void salida(int k) {
            double tt = fin[k];
            Cliente cli = servidores[k];
            servidores[k] = null;
            fin[k] = Double.POSITIVE_INFINITY;
            nSalidas++;
            salieron.add(cli);

            if (!cola.isEmpty()) iniciar(cola.remove(0), k, tt);
        }

        /** Avanza hasta tObjetivo o hasta cumplir la condición de terminación. */
        void avanzar(double tObjetivo) {
            while (true) {
                if (terminado()) break;

                int k = 0;
                for (int i = 1; i < c; i++) if (fin[i] < fin[k]) k = i;
                double tEv = Math.min(sigLlegada, fin[k]);

                if (tEv > tObjetivo) break;

                acumular(tEv);

                if (sigLlegada <= fin[k]) llegada();
                else salida(k);
            }
            acumular(tObjetivo);
            t = tObjetivo;
        }

        /** Tiempo medio de espera en cola observado hasta el momento. */
        double wq() {
            return nInicio > 0 ? sumaEspera / nInicio : 0.0;
        }

        /** Guarda un punto del estado actual para las gráficas. */
        void muestrear() {
            historial.addLast(new double[]{t, cola.size(), wq()});
            if (historial.size() > 3000) historial.removeFirst();
        }

        boolean terminado() {
            return nSalidas >= MAX_ATENDIDOS && cola.isEmpty() && ocupados() == 0;
        }
    }

    // =========================================================================
    // INTERFAZ GRÁFICA (Swing)
    // =========================================================================
    static class App {
        JFrame raiz;
        int c = SERVIDORES_INICIAL;
        Modelo modelo = new Modelo(c);
        boolean corriendo = false;
        long ultimoTick = System.nanoTime();
        int contador = 0;
        JButton btn;
        JSlider vel;
        JLabel velEtiqueta;
        JSpinner spinC;
        JTextArea stats;
        PanelLienzo lienzo;
        PanelGraf graf;
        App() {
            raiz = new JFrame(TITULO);
            raiz.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            raiz.setResizable(false);
            raiz.setLayout(new BorderLayout());
            // Barra de controles
            JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
            btn = new JButton("Iniciar");
            btn.setPreferredSize(new Dimension(90, 28));
            btn.addActionListener(e -> alternar());
            barra.add(btn);
            JButton bReiniciar = new JButton("Reiniciar");
            bReiniciar.setPreferredSize(new Dimension(90, 28));
            bReiniciar.addActionListener(e -> reiniciar());
            barra.add(bReiniciar);

            JButton bSalto = new JButton("Salto rápido (+200)");
            bSalto.addActionListener(e -> salto());
            barra.add(bSalto);

            barra.add(new JLabel("   Velocidad (" + UNIDAD + " simuladas/seg):"));
            // Slider de 1 a 60 = 0.5 a 30 con resolución 0.5
            vel = new JSlider(1, 60, (int) Math.round(VELOCIDAD_INICIAL * 2));
            vel.setPreferredSize(new Dimension(150, 28));
            velEtiqueta = new JLabel(String.format(Locale.US, "%.1f", velocidad()));
            vel.addChangeListener(e -> velEtiqueta.setText(String.format(Locale.US, "%.1f", velocidad())));
            barra.add(vel);
            barra.add(velEtiqueta);

            if (SERVIDORES_EDITABLES) {
                barra.add(new JLabel("   " + NOMBRE_SERVIDOR + "s:"));
                spinC = new JSpinner(new SpinnerNumberModel(c, 1, 8, 1));
                spinC.addChangeListener(e -> cambiarServidores());
                barra.add(spinC);
            }
            raiz.add(barra, BorderLayout.NORTH);

            // Lienzo de animación
            lienzo = new PanelLienzo();
            lienzo.setPreferredSize(new Dimension(ANCHO, ALTO));
            lienzo.setBackground(COLOR_FONDO);
            raiz.add(lienzo, BorderLayout.CENTER);

            // Panel inferior
            JPanel inferior = new JPanel(new BorderLayout());
            stats = new JTextArea();
            stats.setEditable(false);
            stats.setFocusable(false);
            stats.setFont(new Font(FAMILIA_MONO, Font.PLAIN, 13));
            stats.setBackground(inferior.getBackground());
            stats.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            stats.setPreferredSize(new Dimension(380, 235));
            inferior.add(stats, BorderLayout.WEST);

            graf = new PanelGraf();
            graf.setPreferredSize(new Dimension(700, 235));
            graf.setBackground(Color.WHITE);
            inferior.add(graf, BorderLayout.CENTER);
            raiz.add(inferior, BorderLayout.SOUTH);

            raiz.pack();
            raiz.setLocationRelativeTo(null);
            raiz.setVisible(true);

            new javax.swing.Timer(30, e -> tick()).start();
        }

        double velocidad() {
            return vel.getValue() / 2.0;
        }

        // ---------------------------------------------------------------------
        // Controles
        // ---------------------------------------------------------------------
        void alternar() {
            corriendo = !corriendo;
            btn.setText(corriendo ? "Pausar" : "Iniciar");
            ultimoTick = System.nanoTime();
        }

        void reiniciar() {
            modelo = new Modelo(c);
            corriendo = false;
            btn.setText("Iniciar");
            raiz.setTitle(TITULO);
        }

        void cambiarServidores() {
            c = Math.max(1, Math.min(8, (Integer) spinC.getValue()));
            reiniciar();
        }

        /** Avanza rápidamente respetando la condición de terminación. */
        void salto() {
            Modelo m = modelo;
            int objetivo = m.nSalidas + 200;
            boolean estable = erlangC(m.c, TASA_LLEGADA, TASA_SERVICIO) != null;

            while (true) {
                if (m.terminado()) break;
                if (m.nSalidas >= objetivo && m.nSalidas >= MAX_ATENDIDOS) {
                    if (m.cola.isEmpty() && m.ocupados() == 0) break;
                }
                // Seguro extra: si rho >= 1 la fila nunca se vacía, así que no esperamos eso.
                if (!estable && m.nSalidas >= objetivo) break;
                m.avanzar(m.t + 50.0 / TASA_LLEGADA);
            }

            m.salieron.clear();
            m.muestrear();

            if (m.terminado()) {
                corriendo = false;
                btn.setText("Iniciar");
                raiz.setTitle(TITULO + "  |  TERMINADA (" + m.nSalidas + " atendidos)");
            }
        }

        // ---------------------------------------------------------------------
        // Ciclo de animación
        // ---------------------------------------------------------------------
        void tick() {
            long ahora = System.nanoTime();
            double dt = Math.min((ahora - ultimoTick) / 1e9, 0.1);
            ultimoTick = ahora;

            if (corriendo) {
                Modelo m = modelo;
                if (m.terminado()) {
                    corriendo = false;
                    btn.setText("Iniciar");
                    raiz.setTitle(TITULO + "  |  TERMINADA (" + m.nSalidas + " atendidos)");
                } else {
                    m.avanzar(m.t + dt * velocidad());
                    contador++;
                    if (contador % 3 == 0) m.muestrear();
                }
            }

            lienzo.repaint();
            graf.repaint();
            stats.setText(textoEstadisticas());
        }

        // ---------------------------------------------------------------------
        // Estadísticas
        // ---------------------------------------------------------------------
        String textoEstadisticas() {
            Modelo m = modelo;
            Teoria teo = erlangC(m.c, TASA_LLEGADA, TASA_SERVICIO);
            double t = m.t;

            double wq = m.wq();
            double w = m.nInicio > 0 ? (m.sumaEspera + m.sumaServicio) / m.nInicio : 0.0;
            double lq = t > 0 ? m.areaCola / t : 0.0;
            double rho = t > 0 ? m.areaOcupados / (m.c * t) : 0.0;

            String nc = NOMBRE_CLIENTES.substring(0, 1).toUpperCase() + NOMBRE_CLIENTES.substring(1);
            String unidadSing = UNIDAD.endsWith("s") ? UNIDAD.substring(0, UNIDAD.length() - 1) : UNIDAD;

            List<String> lineas = new ArrayList<>();
            lineas.add(String.format(Locale.US, "%s llegados: %d   atendidos: %d", nc, m.nLlegadas, m.nSalidas));
            lineas.add(String.format(Locale.US, "En fila: %d   En servicio: %d", m.cola.size(), m.ocupados()));
            lineas.add(String.format(Locale.US, "lambda = %.2f   mu = %.2f  (por %s)", TASA_LLEGADA, TASA_SERVICIO, unidadSing));
            lineas.add("");
            lineas.add(String.format(Locale.US, "%-22s %9s %10s", "Medida", "Simulación", "Teoría"));
            lineas.add(String.format(Locale.US, "%-22s %9.3f %10s", "Wq espera en fila", wq, teoria(teo, "Wq")));
            lineas.add(String.format(Locale.US, "%-22s %9.3f %10s", "W  tiempo en sistema", w, teoria(teo, "W")));
            lineas.add(String.format(Locale.US, "%-22s %9.3f %10s", "Lq en fila (prom.)", lq, teoria(teo, "Lq")));
            lineas.add(String.format(Locale.US, "%-22s %9.3f %10s", "rho utilización", rho, teoria(teo, "rho")));
            lineas.add("");
            lineas.add("Unidad de tiempo: " + UNIDAD);

            if (teo == null) lineas.add("rho >= 1: la fila crece sin límite");

            if (m.terminado()) {
                lineas.add("");
                lineas.add("*** SIMULACIÓN TERMINADA ***");
                lineas.add("(sistema vacío - " + m.nSalidas + " atendidos)");
            }
            return String.join("\n", lineas);
        }

        static String teoria(Teoria teo, String k) {
            return teo != null ? String.format(Locale.US, "%9.3f", teo.get(k)) : "  inestable";
        }

        // ---------------------------------------------------------------------
        // Panel de animación
        // ---------------------------------------------------------------------
        class PanelLienzo extends JPanel {

            double[] geometriaServidor(int k) {
                int cc = modelo.c;
                double arriba = 40, abajo = ALTO - 25;
                double hueco = (abajo - arriba) / cc;
                double alto = Math.min(72, hueco - 16);
                double yc = arriba + hueco * (k + 0.5);
                return new double[]{SERV_X, yc - alto / 2, CAJA_ANCHO, alto};
            }

            void caja3d(Graphics2D g, double x, double y, double w, double h, Color color) {
                double prof = 16;
                double d = prof * 0.7;
                Color borde = Color.decode("#444444");
                poligono(g, escalar(color, 1.15), borde,
                        x, y, x + prof, y - d, x + w + prof, y - d, x + w, y);
                poligono(g, escalar(color, 0.75), borde,
                        x + w, y, x + w + prof, y - d, x + w + prof, y + h - d, x + w, y + h);
                poligono(g, color, borde, x, y, x + w, y, x + w, y + h, x, y + h);
            }

            void dibujarCliente(Graphics2D g, double x, double y, Color color) {
                Color borde = Color.decode("#333333");
                if (FORMA_CLIENTE.equals("barco")) {
                    poligono(g, color, borde,
                            x - 17, y - 2, x + 17, y - 2, x + 10, y + 9, x - 10, y + 9);
                    g.setColor(borde);
                    g.setStroke(new BasicStroke(2f));
                    g.draw(new java.awt.geom.Line2D.Double(x, y - 2, x, y - 24));
                    g.setStroke(new BasicStroke(1f));
                    poligono(g, Color.WHITE, borde, x + 1, y - 23, x + 14, y - 5, x + 1, y - 5);
                } else {
                    g.setColor(Color.decode("#f1c9a5"));
                    g.fill(new java.awt.geom.Ellipse2D.Double(x - 5, y - 17, 10, 10));
                    g.setColor(borde);
                    g.draw(new java.awt.geom.Ellipse2D.Double(x - 5, y - 17, 10, 10));
                    g.setColor(color);
                    g.fill(new java.awt.geom.Ellipse2D.Double(x - 9, y - 7, 18, 19));
                    g.setColor(borde);
                    g.draw(new java.awt.geom.Ellipse2D.Double(x - 9, y - 7, 18, 19));
                }
            }

            void mover(Cliente cli, double tx, double ty) {
                if (Double.isNaN(cli.x)) {
                    cli.x = X_ENTRADA;
                    cli.y = ALTO / 2.0;
                }
                cli.x += (tx - cli.x) * 0.22;
                cli.y += (ty - cli.y) * 0.22;
            }

            @Override
            protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                Modelo m = modelo;
                Color negro = Color.BLACK;

                texto(g, "Llegadas", X_ENTRADA - 20, 16, "w", fuente(Font.BOLD, 10), negro);
                texto(g, "Fila de espera (orden de llegada)",
                        X_COLA_CABEZA - MAX_VISIBLES * SEP / 2.0 + 20, 16, "c", fuente(Font.BOLD, 10), negro);
                texto(g, NOMBRE_SERVIDOR + (m.c > 1 ? "s" : ""), SERV_X, 16, "w", fuente(Font.BOLD, 10), negro);

                g.setColor(COLOR_CARRIL);
                g.fill(new java.awt.geom.Rectangle2D.Double(X_ENTRADA - 25, ALTO / 2.0 - 24,
                        (X_COLA_CABEZA + 25) - (X_ENTRADA - 25), 48));

                for (int k = 0; k < m.c; k++) {
                    double[] r = geometriaServidor(k);
                    boolean ocupado = m.servidores[k] != null;
                    caja3d(g, r[0], r[1], r[2], r[3], ocupado ? COLOR_SERV_OCUPADO : COLOR_SERV_LIBRE);
                    String nombre = NOMBRE_SERVIDOR + (m.c > 1 ? " " + (k + 1) : "");
                    texto(g, nombre, r[0] + 8, r[1] + 12, "w", fuente(Font.BOLD, 9), negro);
                    texto(g, ocupado ? "ocupado" : "libre", r[0] + 8, r[1] + r[3] - 12, "w",
                            fuente(Font.PLAIN, 8), negro);
                }

                double ref = 3.0 / TASA_SERVICIO;
                int nVis = Math.min(MAX_VISIBLES, m.cola.size());
                for (int i = 0; i < nVis; i++)
                    mover(m.cola.get(i), X_COLA_CABEZA - i * SEP, ALTO / 2.0);
                for (int i = MAX_VISIBLES; i < m.cola.size(); i++)
                    mover(m.cola.get(i), X_COLA_CABEZA - (MAX_VISIBLES - 1) * SEP, ALTO / 2.0);

                for (int i = 0; i < nVis; i++) {
                    Cliente cli = m.cola.get(i);
                    double f = Math.min(1.0, (m.t - cli.llegada) / ref);
                    dibujarCliente(g, cli.x, cli.y, mezclar(VERDE, ROJO, f));
                }

                if (m.cola.size() > MAX_VISIBLES) {
                    texto(g, "+" + (m.cola.size() - MAX_VISIBLES) + " más",
                            X_COLA_CABEZA - (MAX_VISIBLES - 1) * SEP - 10, ALTO / 2.0 + 38, "c",
                            fuente(Font.BOLD, 10), ROJO);
                }

                for (int k = 0; k < m.c; k++) {
                    Cliente cli = m.servidores[k];
                    if (cli != null) {
                        double[] r = geometriaServidor(k);
                        mover(cli, r[0] + r[2] * 0.74, r[1] + r[3] / 2);
                        dibujarCliente(g, cli.x, cli.y, AZUL);
                    }
                }

                List<Cliente> quedan = new ArrayList<>();
                for (Cliente cli : m.salieron) {
                    if (Double.isNaN(cli.x)) continue;
                    mover(cli, ANCHO + 80, cli.y);
                    if (cli.x < ANCHO + 20) {
                        dibujarCliente(g, cli.x, cli.y, Color.decode("#9aa0a6"));
                        quedan.add(cli);
                    }
                }
                m.salieron = quedan;

                texto(g, String.format(Locale.US, "t = %.1f %s", m.t, UNIDAD),
                        ANCHO - 10, ALTO - 10, "e", fuente(Font.PLAIN, 10), negro);
                g.dispose();
            }
        }

        // ---------------------------------------------------------------------
        // Panel de gráficas
        // ---------------------------------------------------------------------
        class PanelGraf extends JPanel {

            void graficar(Graphics2D g, double x0, double y0, double x1, double y1,
                          List<Double> datos, String titulo, Color color, Double ref, boolean entero) {
                texto(g, titulo, (x0 + x1) / 2, y0 - 16, "c", fuente(Font.BOLD, 9), Color.BLACK);
                g.setColor(Color.decode("#888888"));
                g.draw(new java.awt.geom.Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0));

                double maxDatos = 0.0;
                for (double v : datos) maxDatos = Math.max(maxDatos, v);
                double ymax = Math.max(maxDatos,
                        Math.max(ref != null && ref != 0 ? ref * 1.2 : 0.0, entero ? 1.0 : 1e-6));
                if (entero) ymax = 4 * Math.ceil(ymax / 1.1 / 4);
                else ymax *= 1.1;

                for (int i = 0; i < 5; i++) {
                    double y = y1 - (y1 - y0) * i / 4;
                    double valor = ymax * i / 4;
                    g.setColor(Color.decode("#e4e4e4"));
                    g.draw(new java.awt.geom.Line2D.Double(x0, y, x1, y));
                    texto(g, entero ? String.format(Locale.US, "%.0f", valor) : String.format(Locale.US, "%.2f", valor),
                            x0 - 4, y, "e", fuente(Font.PLAIN, 8), Color.BLACK);
                }

                if (datos.size() > 1) {
                    Path2D.Double path = new Path2D.Double();
                    int n = datos.size();
                    for (int i = 0; i < n; i++) {
                        double px = x0 + (x1 - x0) * i / (n - 1);
                        double py = y1 - (y1 - y0) * datos.get(i) / ymax;
                        if (i == 0) path.moveTo(px, py);
                        else path.lineTo(px, py);
                    }
                    g.setColor(color);
                    g.setStroke(new BasicStroke(2f));
                    g.draw(path);
                    g.setStroke(new BasicStroke(1f));
                }

                if (ref != null) {
                    double yr = y1 - (y1 - y0) * ref / ymax;
                    g.setColor(ROJO);
                    g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                            10f, new float[]{6f, 4f}, 0f));
                    g.draw(new java.awt.geom.Line2D.Double(x0, yr, x1, yr));
                    g.setStroke(new BasicStroke(1f));
                    texto(g, "teoría", x1 - 4, yr - 8, "e", fuente(Font.PLAIN, 8), ROJO);
                }
            }

            @Override
            protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                Modelo m = modelo;
                List<double[]> hist = new ArrayList<>(m.historial);
                Teoria teo = erlangC(m.c, TASA_LLEGADA, TASA_SERVICIO);

                List<Double> colas = new ArrayList<>();
                List<Double> wqs = new ArrayList<>();
                for (double[] h : hist) {
                    colas.add(h[1]);
                    wqs.add(h[2]);
                }

                graficar(g, 45, 40, 335, 190, colas, "Clientes en la fila", AZUL, null, true);
                graficar(g, 395, 40, 685, 190, wqs,
                        "Espera promedio Wq acumulada (" + UNIDAD + ")",
                        Color.decode("#e08a1e"), teo != null ? teo.wq : null, false);

                if (!hist.isEmpty()) {
                    String txt = String.format(Locale.US, "tiempo simulado: %.1f a %.1f %s",
                            hist.get(0)[0], hist.get(hist.size() - 1)[0], UNIDAD);
                    texto(g, txt, 350, 215, "c", fuente(Font.PLAIN, 8), Color.decode("#555555"));
                }
                g.dispose();
            }
        }
    }

    // =========================================================================
    // PUNTO DE ENTRADA
    // =========================================================================
    public static void main(String[] args) {
        if (SEMILLA != null) RND = new Random(SEMILLA);
        SwingUtilities.invokeLater(App::new);
    }
}
