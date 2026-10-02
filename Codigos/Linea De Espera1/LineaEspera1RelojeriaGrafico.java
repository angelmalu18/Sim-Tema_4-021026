/*
 * ============================================================================
 * LÍNEA DE ESPERA 1 - UN SERVIDOR (M/M/1)
 * Tema 4.3.1 - Problemas con líneas de espera
 * Ejemplo: RELOJERÍA con un solo relojero
 * ============================================================================
 *
 * DESCRIPCIÓN DEL SISTEMA
 * -----------------------
 * - Un solo relojero atiende reparaciones rápidas (cambio de pila, ajuste de
 *   correa, limpieza).
 * - Los clientes llegan según una distribución exponencial con tasa λ = 2
 *   clientes/hora.
 * - El tiempo de servicio también es exponencial con tasa μ = 2.5 clientes/hora
 *   (es decir, el relojero tarda en promedio 24 minutos por cliente).
 * - Disciplina de cola: FIFO (First In, First Out).
 * - Sistema M/M/1 (llegadas Markovianas, servicio Markoviano, 1 servidor).
 *
 * OBJETIVO DE LA SIMULACIÓN
 * -------------------------
 * Simular el sistema de forma visual y compararlo con las fórmulas teóricas de
 * colas M/M/1 (Wq, W, Lq, ρ).
 *
 * CONDICIÓN DE TERMINACIÓN
 * ------------------------
 * La simulación se detiene automáticamente cuando se cumplen las tres
 * condiciones:
 *   1. Se han atendido al menos MAX_ATENDIDOS clientes (por defecto 800).
 *   2. No queda nadie en la fila de espera.
 *   3. El servidor está libre.
 *
 * CÓMO EJECUTAR
 * -------------
 *     javac LineaEspera1RelojeriaGrafico.java
 *     java LineaEspera1RelojeriaGrafico
 *
 *   (o directamente:  java LineaEspera1RelojeriaGrafico.java)
 */

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class LineaEspera1RelojeriaGrafico {

    // =========================================================================
    // CONFIGURACIÓN DEL EJEMPLO
    // =========================================================================
    static final String TITULO = "LÍNEA DE ESPERA 1 - UN SERVIDOR (M/M/1)  |  Relojería";
    static final String NOMBRE_CLIENTES = "clientes";
    static final String NOMBRE_SERVIDOR = "Relojero";
    static final String UNIDAD = "horas";

    // Parámetros del sistema M/M/1
    static final double TASA_LLEGADA = 2.0;     // λ = clientes por hora
    static final double TASA_SERVICIO = 2.5;    // μ = clientes por hora (24 min por cliente)

    // Configuración de la simulación
    static final int SERVIDORES_INICIAL = 1;
    static final boolean SERVIDORES_EDITABLES = false;
    static final double VELOCIDAD_INICIAL = 2.0; // horas simuladas por segundo real
    static final int MAX_ATENDIDOS = 800;        // número mínimo de clientes a atender
    static final Long SEMILLA = null;            // null = cada ejecución distinta; ej. 42L para repetir

    // Apariencia
    static final String FORMA_CLIENTE = "persona";  // "persona" o "barco"
    static final Color COLOR_FONDO = hex("#f3f5f7");
    static final Color COLOR_CARRIL = hex("#dfe4e8");
    static final Color COLOR_SERV_LIBRE = hex("#9fd8a6");
    static final Color COLOR_SERV_OCUPADO = hex("#f2b26b");

    static final Random RNG = (SEMILLA != null) ? new Random(SEMILLA) : new Random();

    // =========================================================================
    // GENERADORES DE VARIABLES ALEATORIAS
    // =========================================================================

    /**
     * Genera un valor de una distribución exponencial con la tasa dada.
     * Usa el método de la transformada inversa: X = -ln(1 - U) / tasa
     * donde U ~ Uniforme(0,1)
     */
    static double exponencial(double tasa) {
        return -Math.log(1.0 - RNG.nextDouble()) / tasa;
    }

    /**
     * Calcula las medidas de desempeño teóricas de un sistema M/M/c.
     * Para c = 1 se reduce a las fórmulas clásicas de M/M/1.
     *
     * Retorna un arreglo {Wq, W, Lq, rho}:
     *     Wq  : tiempo medio de espera en cola
     *     W   : tiempo medio en el sistema
     *     Lq  : número medio de clientes en cola
     *     rho : utilización del sistema
     *
     * Retorna null si el sistema es inestable (ρ ≥ 1).
     */
    static double[] erlangC(int c, double tasaLl, double tasaSv) {
        double a = tasaLl / tasaSv;
        double rho = a / c;
        if (rho >= 1) {
            return null;
        }

        double suma = 0.0;
        for (int k = 0; k < c; k++) {
            suma += Math.pow(a, k) / factorial(k);
        }
        double ultimo = Math.pow(a, c) / (factorial(c) * (1 - rho));
        double probEsperar = ultimo / (suma + ultimo);

        double lq = probEsperar * rho / (1 - rho);
        double wq = lq / tasaLl;
        return new double[]{wq, wq + 1 / tasaSv, lq, rho};
    }

    static final int WQ = 0, W = 1, LQ = 2, RHO = 3;

    static double factorial(int n) {
        double f = 1.0;
        for (int i = 2; i <= n; i++) f *= i;
        return f;
    }

    // =========================================================================
    // UTILIDADES DE COLOR
    // =========================================================================

    /** Convierte un color hexadecimal (#rrggbb) a Color. */
    static Color hex(String color) {
        return new Color(Integer.parseInt(color.substring(1, 3), 16),
                         Integer.parseInt(color.substring(3, 5), 16),
                         Integer.parseInt(color.substring(5, 7), 16));
    }

    static int limitar(double v) {
        return Math.max(0, Math.min(255, (int) v));
    }

    /** Mezcla linealmente dos colores. f=0 → c1, f=1 → c2. */
    static Color mezclar(Color c1, Color c2, double f) {
        return new Color(
                limitar(c1.getRed() + (c2.getRed() - c1.getRed()) * f),
                limitar(c1.getGreen() + (c2.getGreen() - c1.getGreen()) * f),
                limitar(c1.getBlue() + (c2.getBlue() - c1.getBlue()) * f));
    }

    /** Oscurece o aclara un color multiplicando sus componentes por factor. */
    static Color escalar(Color color, double factor) {
        return new Color(limitar(color.getRed() * factor),
                         limitar(color.getGreen() * factor),
                         limitar(color.getBlue() * factor));
    }

    // =========================================================================
    // MODELO DE SIMULACIÓN DE EVENTOS DISCRETOS
    // =========================================================================

    /** Representa a un cliente individual. */
    static class Cliente {
        double llegada;     // instante de llegada al sistema
        double servicio;    // duración del servicio
        Double x = null;    // posición horizontal en pantalla
        Double y = null;    // posición vertical en pantalla

        Cliente(double llegada, double servicio) {
            this.llegada = llegada;
            this.servicio = servicio;
        }
    }

    /**
     * Modelo de simulación de eventos discretos para una cola M/M/c.
     *
     * Eventos principales:
     *     - Llegada de un cliente
     *     - Fin de servicio (salida de un cliente)
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
        ArrayDeque<double[]> historial;     // (t, largo de cola, Wq)

        Modelo(int c) {
            this.c = c;
            reiniciar();
        }

        /** Reinicia el estado del modelo a cero. */
        void reiniciar() {
            t = 0.0;
            ultimo = 0.0;
            sigLlegada = exponencial(TASA_LLEGADA);
            servidores = new Cliente[c];
            fin = new double[c];
            java.util.Arrays.fill(fin, Double.POSITIVE_INFINITY);
            cola = new ArrayList<>();
            salieron = new ArrayList<>();
            nLlegadas = 0;
            nInicio = 0;
            nSalidas = 0;
            sumaEspera = 0.0;
            sumaServicio = 0.0;
            areaCola = 0.0;
            areaOcupados = 0.0;
            historial = new ArrayDeque<>();
        }

        /** Devuelve cuántos servidores están ocupados. */
        int ocupados() {
            int n = 0;
            for (Cliente s : servidores) if (s != null) n++;
            return n;
        }

        /** Acumula las áreas bajo las curvas de Lq y de utilización. */
        void acumular(double t) {
            double dt = t - ultimo;
            areaCola += cola.size() * dt;
            areaOcupados += ocupados() * dt;
            ultimo = t;
        }

        /** Asigna el cliente cli al servidor k en el instante t. */
        void iniciar(Cliente cli, int k, double t) {
            sumaEspera += t - cli.llegada;
            sumaServicio += cli.servicio;
            nInicio++;
            servidores[k] = cli;
            fin[k] = t + cli.servicio;
        }

        /** Procesa el evento de llegada de un nuevo cliente. */
        void llegada() {
            double t = sigLlegada;
            Cliente cli = new Cliente(t, exponencial(TASA_SERVICIO));
            nLlegadas++;

            int libre = -1;
            for (int k = 0; k < c; k++) {
                if (servidores[k] == null) {
                    libre = k;
                    break;
                }
            }

            if (libre >= 0) {
                iniciar(cli, libre, t);
            } else {
                cola.add(cli);
            }

            sigLlegada = t + exponencial(TASA_LLEGADA);
        }

        /** Procesa el evento de fin de servicio del servidor k. */
        void salida(int k) {
            double t = fin[k];
            Cliente cli = servidores[k];
            servidores[k] = null;
            fin[k] = Double.POSITIVE_INFINITY;
            nSalidas++;
            salieron.add(cli);

            if (!cola.isEmpty()) {
                iniciar(cola.remove(0), k, t);
            }
        }

        /**
         * Avanza la simulación hasta tObjetivo o hasta cumplir la condición
         * de terminación (sistema vacío después de atender suficientes clientes).
         */
        void avanzar(double tObjetivo) {
            while (true) {
                if (terminado()) {
                    break;
                }

                int k = 0;
                for (int i = 1; i < c; i++) {
                    if (fin[i] < fin[k]) k = i;
                }
                double tEv = Math.min(sigLlegada, fin[k]);

                if (tEv > tObjetivo) {
                    break;
                }

                acumular(tEv);

                if (sigLlegada <= fin[k]) {
                    llegada();
                } else {
                    salida(k);
                }
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

        /** Indica si se cumplió la condición de terminación. */
        boolean terminado() {
            return nSalidas >= MAX_ATENDIDOS && cola.isEmpty() && ocupados() == 0;
        }
    }

    // =========================================================================
    // INTERFAZ GRÁFICA (Swing)
    // =========================================================================
    static final int ANCHO = 1000;
    static final int ALTO = 330;
    static final int X_ENTRADA = 45;
    static final int X_COLA_CABEZA = 470;
    static final int SEP = FORMA_CLIENTE.equals("barco") ? 40 : 28;
    static final int MAX_VISIBLES = (X_COLA_CABEZA - (X_ENTRADA + 30)) / SEP;
    static final int SERV_X = 620;
    static final int CAJA_ANCHO = 160;
    static final Color VERDE = hex("#2e9e4f");
    static final Color ROJO = hex("#d33a2c");
    static final Color AZUL = hex("#3b7dd8");
    static final Color GRIS_BORDE = hex("#444444");
    static final Color GRIS_CLI = hex("#333333");

    // ---- Utilidades de dibujo (equivalentes a create_* del Canvas) -----------
    static Font fuente(int tam, boolean negrita) {
        return new Font("Segoe UI", negrita ? Font.BOLD : Font.PLAIN, tam + 3);
    }

    static void poligono(Graphics2D g, Color relleno, Color borde, double... p) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(p[0], p[1]);
        for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
        path.closePath();
        if (relleno != null) { g.setColor(relleno); g.fill(path); }
        if (borde != null) { g.setStroke(new BasicStroke(1f)); g.setColor(borde); g.draw(path); }
    }

    static void rectangulo(Graphics2D g, double x0, double y0, double x1, double y1,
                           Color relleno, Color borde) {
        Rectangle2D.Double r = new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0);
        if (relleno != null) { g.setColor(relleno); g.fill(r); }
        if (borde != null) { g.setStroke(new BasicStroke(1f)); g.setColor(borde); g.draw(r); }
    }

    static void ovalo(Graphics2D g, double x0, double y0, double x1, double y1,
                      Color relleno, Color borde) {
        Ellipse2D.Double o = new Ellipse2D.Double(x0, y0, x1 - x0, y1 - y0);
        if (relleno != null) { g.setColor(relleno); g.fill(o); }
        if (borde != null) { g.setStroke(new BasicStroke(1f)); g.setColor(borde); g.draw(o); }
    }

    static void linea(Graphics2D g, double x0, double y0, double x1, double y1,
                      Color color, float ancho, float[] guion) {
        g.setColor(color);
        g.setStroke(guion == null
                ? new BasicStroke(ancho)
                : new BasicStroke(ancho, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, guion, 0f));
        g.draw(new java.awt.geom.Line2D.Double(x0, y0, x1, y1));
        g.setStroke(new BasicStroke(1f));
    }

    /** anchor: "w" (izquierda), "e" (derecha) o "c" (centro); centrado en vertical. */
    static void texto(Graphics2D g, String s, double x, double y, String anchor,
                      Font f, Color color) {
        g.setFont(f);
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        double w = fm.stringWidth(s);
        double px = anchor.equals("w") ? x : anchor.equals("e") ? x - w : x - w / 2;
        double py = y + (fm.getAscent() - fm.getDescent()) / 2.0;
        g.drawString(s, (float) px, (float) py);
    }

    /** Aplicación principal con animación y controles. */
    static class App {
        JFrame raiz;
        int c;
        Modelo modelo;
        boolean corriendo = false;
        long ultimoTick = System.nanoTime();
        int contador = 0;

        JButton btn;
        JSlider vel;
        JLabel velValor;
        JSpinner spinC;
        JTextArea stats;
        JPanel lienzo, graf;

        App(JFrame raiz) {
            this.raiz = raiz;
            raiz.setTitle(TITULO);
            raiz.setResizable(false);
            raiz.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            c = SERVIDORES_INICIAL;
            modelo = new Modelo(c);

            // Barra de controles
            JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));

            btn = new JButton("Iniciar");
            btn.setPreferredSize(new Dimension(100, 28));
            btn.addActionListener(e -> alternar());
            barra.add(btn);

            JButton bReiniciar = new JButton("Reiniciar");
            bReiniciar.setPreferredSize(new Dimension(100, 28));
            bReiniciar.addActionListener(e -> reiniciar());
            barra.add(bReiniciar);

            JButton bSalto = new JButton("Salto rápido (+200)");
            bSalto.addActionListener(e -> salto());
            barra.add(bSalto);

            barra.add(new JLabel("   Velocidad (" + UNIDAD + " simuladas/seg):"));
            vel = new JSlider(1, 60, (int) Math.round(VELOCIDAD_INICIAL * 2));  // 0.5 a 30, paso 0.5
            vel.setPreferredSize(new Dimension(150, 28));
            velValor = new JLabel(String.format(Locale.US, "%.1f", velocidad()));
            vel.addChangeListener(e -> velValor.setText(String.format(Locale.US, "%.1f", velocidad())));
            barra.add(vel);
            barra.add(velValor);

            if (SERVIDORES_EDITABLES) {
                barra.add(new JLabel("   " + NOMBRE_SERVIDOR + "s:"));
                spinC = new JSpinner(new SpinnerNumberModel(c, 1, 8, 1));
                spinC.addChangeListener(e -> cambiarServidores());
                barra.add(spinC);
            }

            // Lienzo de animación
            lienzo = new JPanel() {
                @Override protected void paintComponent(Graphics g0) {
                    super.paintComponent(g0);
                    Graphics2D g = (Graphics2D) g0;
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    dibujar(g);
                }
            };
            lienzo.setPreferredSize(new Dimension(ANCHO, ALTO));
            lienzo.setBackground(COLOR_FONDO);

            // Panel inferior
            JPanel inferior = new JPanel(new BorderLayout());

            stats = new JTextArea();
            stats.setEditable(false);
            stats.setFocusable(false);
            stats.setOpaque(false);
            stats.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            stats.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            stats.setPreferredSize(new Dimension(340, 235));
            inferior.add(stats, BorderLayout.WEST);

            graf = new JPanel() {
                @Override protected void paintComponent(Graphics g0) {
                    super.paintComponent(g0);
                    Graphics2D g = (Graphics2D) g0;
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    dibujarGraficas(g);
                }
            };
            graf.setPreferredSize(new Dimension(700, 235));
            graf.setBackground(Color.WHITE);
            inferior.add(graf, BorderLayout.CENTER);

            raiz.add(barra, BorderLayout.NORTH);
            raiz.add(lienzo, BorderLayout.CENTER);
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

        /** Inicia o pausa la simulación. */
        void alternar() {
            corriendo = !corriendo;
            btn.setText(corriendo ? "Pausar" : "Iniciar");
            ultimoTick = System.nanoTime();
        }

        /** Reinicia completamente la simulación. */
        void reiniciar() {
            modelo = new Modelo(c);
            corriendo = false;
            btn.setText("Iniciar");
            raiz.setTitle(TITULO);
        }

        /** Cambia el número de servidores (si está habilitado). */
        void cambiarServidores() {
            c = Math.max(1, Math.min(8, (Integer) spinC.getValue()));
            reiniciar();
        }

        /** Avanza rápidamente la simulación respetando la condición de terminación. */
        void salto() {
            Modelo m = modelo;
            int objetivo = m.nSalidas + 200;

            while (true) {
                if (m.terminado()) {
                    break;
                }
                if (m.nSalidas >= objetivo && m.nSalidas >= MAX_ATENDIDOS) {
                    if (m.cola.isEmpty() && m.ocupados() == 0) {
                        break;
                    }
                }
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
                    if (contador % 3 == 0) {
                        m.muestrear();
                    }
                }
            }

            dibujarEstadisticas();
            lienzo.repaint();
            graf.repaint();
        }

        // ---------------------------------------------------------------------
        // Dibujo
        // ---------------------------------------------------------------------
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
            poligono(g, escalar(color, 1.15), GRIS_BORDE,
                    x, y, x + prof, y - d, x + w + prof, y - d, x + w, y);
            poligono(g, escalar(color, 0.75), GRIS_BORDE,
                    x + w, y, x + w + prof, y - d, x + w + prof, y + h - d, x + w, y + h);
            rectangulo(g, x, y, x + w, y + h, color, GRIS_BORDE);
        }

        void dibujarCliente(Graphics2D g, double x, double y, Color color) {
            if (FORMA_CLIENTE.equals("barco")) {
                poligono(g, color, GRIS_CLI,
                        x - 17, y - 2, x + 17, y - 2, x + 10, y + 9, x - 10, y + 9);
                linea(g, x, y - 2, x, y - 24, GRIS_CLI, 2f, null);
                poligono(g, Color.WHITE, GRIS_CLI,
                        x + 1, y - 23, x + 14, y - 5, x + 1, y - 5);
            } else {
                ovalo(g, x - 5, y - 17, x + 5, y - 7, hex("#f1c9a5"), GRIS_CLI);
                ovalo(g, x - 9, y - 7, x + 9, y + 12, color, GRIS_CLI);
            }
        }

        void mover(Cliente cli, double tx, double ty) {
            if (cli.x == null) {
                cli.x = (double) X_ENTRADA;
                cli.y = ALTO / 2.0;
            }
            cli.x += (tx - cli.x) * 0.22;
            cli.y += (ty - cli.y) * 0.22;
        }

        void dibujar(Graphics2D g) {
            Modelo m = modelo;

            texto(g, "Llegadas", X_ENTRADA - 20, 16, "w", fuente(10, true), Color.BLACK);
            texto(g, "Fila de espera (orden de llegada)",
                    X_COLA_CABEZA - MAX_VISIBLES * SEP / 2.0 + 20, 16, "c",
                    fuente(10, true), Color.BLACK);
            texto(g, NOMBRE_SERVIDOR + (m.c > 1 ? "s" : ""), SERV_X, 16, "w",
                    fuente(10, true), Color.BLACK);

            rectangulo(g, X_ENTRADA - 25, ALTO / 2.0 - 24,
                    X_COLA_CABEZA + 25, ALTO / 2.0 + 24, COLOR_CARRIL, null);

            for (int k = 0; k < m.c; k++) {
                double[] geo = geometriaServidor(k);
                double x = geo[0], y = geo[1], w = geo[2], h = geo[3];
                boolean ocupado = m.servidores[k] != null;
                caja3d(g, x, y, w, h, ocupado ? COLOR_SERV_OCUPADO : COLOR_SERV_LIBRE);
                String nombre = NOMBRE_SERVIDOR + (m.c > 1 ? " " + (k + 1) : "");
                texto(g, nombre, x + 8, y + 12, "w", fuente(9, true), Color.BLACK);
                texto(g, ocupado ? "ocupado" : "libre", x + 8, y + h - 12, "w",
                        fuente(8, false), Color.BLACK);
            }

            double ref = 3.0 / TASA_SERVICIO;
            int visibles = Math.min(m.cola.size(), MAX_VISIBLES);
            for (int i = 0; i < visibles; i++) {
                mover(m.cola.get(i), X_COLA_CABEZA - i * SEP, ALTO / 2.0);
            }
            for (int i = MAX_VISIBLES; i < m.cola.size(); i++) {
                mover(m.cola.get(i), X_COLA_CABEZA - (MAX_VISIBLES - 1) * SEP, ALTO / 2.0);
            }

            for (int i = 0; i < visibles; i++) {
                Cliente cli = m.cola.get(i);
                double f = Math.min(1.0, (m.t - cli.llegada) / ref);
                dibujarCliente(g, cli.x, cli.y, mezclar(VERDE, ROJO, f));
            }

            if (m.cola.size() > MAX_VISIBLES) {
                texto(g, "+" + (m.cola.size() - MAX_VISIBLES) + " más",
                        X_COLA_CABEZA - (MAX_VISIBLES - 1) * SEP - 10, ALTO / 2.0 + 38, "c",
                        fuente(10, true), ROJO);
            }

            for (int k = 0; k < m.servidores.length; k++) {
                Cliente cli = m.servidores[k];
                if (cli != null) {
                    double[] geo = geometriaServidor(k);
                    mover(cli, geo[0] + geo[2] * 0.74, geo[1] + geo[3] / 2);
                    dibujarCliente(g, cli.x, cli.y, AZUL);
                }
            }

            List<Cliente> quedan = new ArrayList<>();
            for (Cliente cli : m.salieron) {
                if (cli.x == null) {
                    continue;
                }
                mover(cli, ANCHO + 80, cli.y);
                if (cli.x < ANCHO + 20) {
                    dibujarCliente(g, cli.x, cli.y, hex("#9aa0a6"));
                    quedan.add(cli);
                }
            }
            m.salieron = quedan;

            texto(g, String.format(Locale.US, "t = %.1f %s", m.t, UNIDAD),
                    ANCHO - 10, ALTO - 10, "e", fuente(10, false), Color.BLACK);
        }

        /** Actualiza el panel de estadísticas. */
        void dibujarEstadisticas() {
            Modelo m = modelo;
            double[] teo = erlangC(m.c, TASA_LLEGADA, TASA_SERVICIO);
            double t = m.t;

            double wq = m.wq();
            double w = m.nInicio > 0 ? (m.sumaEspera + m.sumaServicio) / m.nInicio : 0.0;
            double lq = t > 0 ? m.areaCola / t : 0.0;
            double rho = t > 0 ? m.areaOcupados / (m.c * t) : 0.0;

            String unidadSing = UNIDAD.endsWith("s") ? UNIDAD.substring(0, UNIDAD.length() - 1) : UNIDAD;
            String clientesCap = NOMBRE_CLIENTES.substring(0, 1).toUpperCase() + NOMBRE_CLIENTES.substring(1);

            List<String> lineas = new ArrayList<>();
            lineas.add(String.format(Locale.US, "%s llegados: %d   atendidos: %d",
                    clientesCap, m.nLlegadas, m.nSalidas));
            lineas.add(String.format(Locale.US, "En fila: %d   En servicio: %d",
                    m.cola.size(), m.ocupados()));
            lineas.add(String.format(Locale.US, "lambda = %.2f   mu = %.2f  (por %s)",
                    TASA_LLEGADA, TASA_SERVICIO, unidadSing));
            lineas.add("");
            lineas.add(String.format(Locale.US, "%-22s %9s %10s", "Medida", "Simulación", "Teoría"));
            lineas.add(String.format(Locale.US, "%-22s %9.3f %10s", "Wq espera en fila", wq, teoria(teo, WQ)));
            lineas.add(String.format(Locale.US, "%-22s %9.3f %10s", "W  tiempo en sistema", w, teoria(teo, W)));
            lineas.add(String.format(Locale.US, "%-22s %9.3f %10s", "Lq en fila (prom.)", lq, teoria(teo, LQ)));
            lineas.add(String.format(Locale.US, "%-22s %9.3f %10s", "rho utilización", rho, teoria(teo, RHO)));
            lineas.add("");
            lineas.add("Unidad de tiempo: " + UNIDAD);

            if (teo == null) {
                lineas.add("rho >= 1: la fila crece sin límite");
            }

            // Mensaje de terminación con la cantidad REAL de atendidos
            if (m.terminado()) {
                lineas.add("");
                lineas.add("*** SIMULACIÓN TERMINADA ***");
                lineas.add("(sistema vacío - " + m.nSalidas + " atendidos)");
            }

            stats.setText(String.join("\n", lineas));
        }

        static String teoria(double[] teo, int k) {
            return teo != null ? String.format(Locale.US, "%9.3f", teo[k]) : "  inestable";
        }

        void graficar(Graphics2D g, double x0, double y0, double x1, double y1,
                      List<Double> datos, String titulo, Color color, Double ref, boolean entero) {
            texto(g, titulo, (x0 + x1) / 2, y0 - 16, "c", fuente(9, true), Color.BLACK);
            rectangulo(g, x0, y0, x1, y1, null, hex("#888888"));

            double maxDatos = 0.0;
            for (double d : datos) maxDatos = Math.max(maxDatos, d);
            double ymax = Math.max(Math.max(maxDatos, ref != null ? ref * 1.2 : 0.0),
                    entero ? 1.0 : 1e-6);
            if (entero) {
                ymax = 4 * Math.ceil(ymax / 1.1 / 4);
            } else {
                ymax *= 1.1;
            }

            for (int i = 0; i < 5; i++) {
                double y = y1 - (y1 - y0) * i / 4;
                double valor = ymax * i / 4;
                linea(g, x0, y, x1, y, hex("#e4e4e4"), 1f, null);
                texto(g, entero ? String.format(Locale.US, "%.0f", valor)
                                : String.format(Locale.US, "%.2f", valor),
                        x0 - 4, y, "e", fuente(8, false), Color.BLACK);
            }

            if (datos.size() > 1) {
                int n = datos.size();
                g.setColor(color);
                g.setStroke(new BasicStroke(2f));
                Path2D.Double path = new Path2D.Double();
                for (int i = 0; i < n; i++) {
                    double px = x0 + (x1 - x0) * i / (n - 1);
                    double py = y1 - (y1 - y0) * datos.get(i) / ymax;
                    if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
                }
                g.draw(path);
                g.setStroke(new BasicStroke(1f));
            }

            if (ref != null) {
                double yr = y1 - (y1 - y0) * ref / ymax;
                linea(g, x0, yr, x1, yr, ROJO, 2f, new float[]{6f, 4f});
                texto(g, "teoría", x1 - 4, yr - 8, "e", fuente(8, false), ROJO);
            }
        }

        void dibujarGraficas(Graphics2D g) {
            Modelo m = modelo;
            List<double[]> hist = new ArrayList<>(m.historial);
            double[] teo = erlangC(m.c, TASA_LLEGADA, TASA_SERVICIO);

            List<Double> colas = new ArrayList<>();
            List<Double> wqs = new ArrayList<>();
            for (double[] h : hist) {
                colas.add(h[1]);
                wqs.add(h[2]);
            }

            graficar(g, 45, 40, 335, 190, colas,
                    "Clientes en la fila", AZUL, null, true);

            graficar(g, 395, 40, 685, 190, wqs,
                    "Espera promedio Wq acumulada (" + UNIDAD + ")",
                    hex("#e08a1e"), teo != null ? teo[WQ] : null, false);

            if (!hist.isEmpty()) {
                String txt = String.format(Locale.US, "tiempo simulado: %.1f a %.1f %s",
                        hist.get(0)[0], hist.get(hist.size() - 1)[0], UNIDAD);
                texto(g, txt, 350, 215, "c", fuente(8, false), hex("#555555"));
            }
        }
    }

    // =========================================================================
    // PUNTO DE ENTRADA
    // =========================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new App(new JFrame()));
    }
}
