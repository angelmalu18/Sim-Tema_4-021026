# -*- coding: utf-8 -*-
"""
================================================================================
LÍNEA DE ESPERA 1 - UN SERVIDOR (M/M/1)
Tema 4.3.1 - Problemas con líneas de espera
Ejemplo: RELOJERÍA con un solo relojero
================================================================================

DESCRIPCIÓN DEL SISTEMA
-----------------------
- Un solo relojero atiende reparaciones rápidas (cambio de pila, ajuste de correa, limpieza).
- Los clientes llegan según una distribución exponencial con tasa λ = 2 clientes/hora.
- El tiempo de servicio también es exponencial con tasa μ = 2.5 clientes/hora
  (es decir, el relojero tarda en promedio 24 minutos por cliente).
- Disciplina de cola: FIFO (First In, First Out).
- Sistema M/M/1 (llegadas Markovianas, servicio Markoviano, 1 servidor).

OBJETIVO DE LA SIMULACIÓN
-------------------------
Simular el sistema de forma visual y compararlo con las fórmulas teóricas de
colas M/M/1 (Wq, W, Lq, ρ).

CONDICIÓN DE TERMINACIÓN
------------------------
La simulación se detiene automáticamente cuando se cumplen las tres condiciones:
  1. Se han atendido al menos MAX_ATENDIDOS clientes (por defecto 800).
  2. No queda nadie en la fila de espera.
  3. El servidor está libre.

De esta forma el sistema termina completamente vacío y las estadísticas
acumuladas son consistentes.

INTERFAZ
--------
- Animación 2D con bloques en perspectiva (2.5D).
- Clientes: color verde → rojo según el tiempo que llevan esperando.
- Estadísticas en vivo vs. valores teóricos.
- Dos gráficas en tiempo real (clientes en fila y Wq acumulada).

CÓMO EJECUTAR
-------------
    python linea_espera_1_relojeria_grafico.py
"""

import math
import random
import time
import tkinter as tk
from collections import deque

# =============================================================================
# CONFIGURACIÓN DEL EJEMPLO
# =============================================================================
TITULO = "LÍNEA DE ESPERA 1 - UN SERVIDOR (M/M/1)  |  Relojería"
NOMBRE_CLIENTES = "clientes"
NOMBRE_SERVIDOR = "Relojero"
UNIDAD = "horas"

# Parámetros del sistema M/M/1
TASA_LLEGADA = 2.0          # λ = clientes por hora
TASA_SERVICIO = 2.5         # μ = clientes por hora (24 min por cliente)

# Configuración de la simulación
SERVIDORES_INICIAL = 1
SERVIDORES_EDITABLES = False
VELOCIDAD_INICIAL = 2.0     # horas simuladas por segundo real
MAX_ATENDIDOS = 800         # número mínimo de clientes que deben ser atendidos
SEMILLA = None              # None = cada ejecución distinta; pon un número (ej. 42) para repetir

# Apariencia
FORMA_CLIENTE = "persona"   # "persona" o "barco"
COLOR_FONDO = "#f3f5f7"
COLOR_CARRIL = "#dfe4e8"
COLOR_SERV_LIBRE = "#9fd8a6"
COLOR_SERV_OCUPADO = "#f2b26b"

# =============================================================================
# GENERADORES DE VARIABLES ALEATORIAS
# =============================================================================
def exponencial(tasa):
    """
    Genera un valor de una distribución exponencial con la tasa dada.
    Usa el método de la transformada inversa:
        X = -ln(1 - U) / tasa
    donde U ~ Uniforme(0,1)
    """
    return -math.log(1.0 - random.random()) / tasa


def erlang_c(c, tasa_ll, tasa_sv):
    """
    Calcula las medidas de desempeño teóricas de un sistema M/M/c.
    Para c = 1 se reduce a las fórmulas clásicas de M/M/1.

    Retorna un diccionario con:
        Wq  : tiempo medio de espera en cola
        W   : tiempo medio en el sistema
        Lq  : número medio de clientes en cola
        rho : utilización del sistema

    Retorna None si el sistema es inestable (ρ ≥ 1).
    """
    a = tasa_ll / tasa_sv
    rho = a / c
    if rho >= 1:
        return None

    suma = sum(a ** k / math.factorial(k) for k in range(c))
    ultimo = a ** c / (math.factorial(c) * (1 - rho))
    prob_esperar = ultimo / (suma + ultimo)

    lq = prob_esperar * rho / (1 - rho)
    wq = lq / tasa_ll
    return {
        "Wq": wq,
        "W": wq + 1 / tasa_sv,
        "Lq": lq,
        "rho": rho
    }


# =============================================================================
# UTILIDADES DE COLOR
# =============================================================================
def a_rgb(color):
    """Convierte un color hexadecimal (#rrggbb) a una tupla (r, g, b)."""
    return int(color[1:3], 16), int(color[3:5], 16), int(color[5:7], 16)


def a_hex(r, g, b):
    """Convierte componentes RGB a un color hexadecimal."""
    return "#%02x%02x%02x" % (
        max(0, min(255, int(r))),
        max(0, min(255, int(g))),
        max(0, min(255, int(b)))
    )


def mezclar(c1, c2, f):
    """Mezcla linealmente dos colores. f=0 → c1, f=1 → c2."""
    r1, g1, b1 = a_rgb(c1)
    r2, g2, b2 = a_rgb(c2)
    return a_hex(
        r1 + (r2 - r1) * f,
        g1 + (g2 - g1) * f,
        b1 + (b2 - b1) * f
    )


def escalar(color, factor):
    """Oscurece o aclara un color multiplicando sus componentes por factor."""
    r, g, b = a_rgb(color)
    return a_hex(r * factor, g * factor, b * factor)


# =============================================================================
# MODELO DE SIMULACIÓN DE EVENTOS DISCRETOS
# =============================================================================
class Cliente:
    """Representa a un cliente individual."""
    def __init__(self, llegada, servicio):
        self.llegada = llegada      # instante de llegada al sistema
        self.servicio = servicio    # duración del servicio
        self.x = None               # posición horizontal en pantalla
        self.y = None               # posición vertical en pantalla


class Modelo:
    """
    Modelo de simulación de eventos discretos para una cola M/M/c.

    Eventos principales:
        - Llegada de un cliente
        - Fin de servicio (salida de un cliente)
    """

    def __init__(self, c):
        self.c = c
        self.reiniciar()

    def reiniciar(self):
        """Reinicia el estado del modelo a cero."""
        self.t = 0.0
        self.ultimo = 0.0
        self.sig_llegada = exponencial(TASA_LLEGADA)
        self.servidores = [None] * self.c
        self.fin = [math.inf] * self.c
        self.cola = []
        self.salieron = []
        self.n_llegadas = 0
        self.n_inicio = 0
        self.n_salidas = 0
        self.suma_espera = 0.0
        self.suma_servicio = 0.0
        self.area_cola = 0.0
        self.area_ocupados = 0.0
        self.historial = deque(maxlen=3000)

    def ocupados(self):
        """Devuelve cuántos servidores están ocupados."""
        return sum(1 for s in self.servidores if s is not None)

    def _acumular(self, t):
        """Acumula las áreas bajo las curvas de Lq y de utilización."""
        dt = t - self.ultimo
        self.area_cola += len(self.cola) * dt
        self.area_ocupados += self.ocupados() * dt
        self.ultimo = t

    def _iniciar(self, cli, k, t):
        """Asigna el cliente cli al servidor k en el instante t."""
        self.suma_espera += t - cli.llegada
        self.suma_servicio += cli.servicio
        self.n_inicio += 1
        self.servidores[k] = cli
        self.fin[k] = t + cli.servicio

    def _llegada(self):
        """Procesa el evento de llegada de un nuevo cliente."""
        t = self.sig_llegada
        cli = Cliente(t, exponencial(TASA_SERVICIO))
        self.n_llegadas += 1

        libre = None
        for k in range(self.c):
            if self.servidores[k] is None:
                libre = k
                break

        if libre is not None:
            self._iniciar(cli, libre, t)
        else:
            self.cola.append(cli)

        self.sig_llegada = t + exponencial(TASA_LLEGADA)

    def _salida(self, k):
        """Procesa el evento de fin de servicio del servidor k."""
        t = self.fin[k]
        cli = self.servidores[k]
        self.servidores[k] = None
        self.fin[k] = math.inf
        self.n_salidas += 1
        self.salieron.append(cli)

        if self.cola:
            self._iniciar(self.cola.pop(0), k, t)

    def avanzar(self, t_objetivo):
        """
        Avanza la simulación hasta t_objetivo o hasta cumplir la condición
        de terminación (sistema vacío después de atender suficientes clientes).
        """
        while True:
            if (self.n_salidas >= MAX_ATENDIDOS and
                len(self.cola) == 0 and
                self.ocupados() == 0):
                break

            k = min(range(self.c), key=lambda i: self.fin[i])
            t_ev = min(self.sig_llegada, self.fin[k])

            if t_ev > t_objetivo:
                break

            self._acumular(t_ev)

            if self.sig_llegada <= self.fin[k]:
                self._llegada()
            else:
                self._salida(k)

        self._acumular(t_objetivo)
        self.t = t_objetivo

    def wq(self):
        """Tiempo medio de espera en cola observado hasta el momento."""
        return self.suma_espera / self.n_inicio if self.n_inicio else 0.0

    def muestrear(self):
        """Guarda un punto del estado actual para las gráficas."""
        self.historial.append((self.t, len(self.cola), self.wq()))

    def terminado(self):
        """Indica si se cumplió la condición de terminación."""
        return (self.n_salidas >= MAX_ATENDIDOS and
                len(self.cola) == 0 and
                self.ocupados() == 0)


# =============================================================================
# INTERFAZ GRÁFICA (tkinter)
# =============================================================================
ANCHO = 1000
ALTO = 330
X_ENTRADA = 45
X_COLA_CABEZA = 470
SEP = 40 if FORMA_CLIENTE == "barco" else 28
MAX_VISIBLES = int((X_COLA_CABEZA - (X_ENTRADA + 30)) / SEP)
SERV_X = 620
CAJA_ANCHO = 160
VERDE = "#2e9e4f"
ROJO = "#d33a2c"
AZUL = "#3b7dd8"


class App:
    """Aplicación principal con animación y controles."""

    def __init__(self, raiz):
        self.raiz = raiz
        raiz.title(TITULO)
        raiz.resizable(False, False)

        self.c = SERVIDORES_INICIAL
        self.modelo = Modelo(self.c)
        self.corriendo = False
        self.ultimo_tick = time.time()
        self.contador = 0

        # Barra de controles
        barra = tk.Frame(raiz, padx=8, pady=6)
        barra.pack(fill="x")

        self.btn = tk.Button(barra, text="Iniciar", width=10, command=self.alternar)
        self.btn.pack(side="left", padx=3)

        tk.Button(barra, text="Reiniciar", width=10, command=self.reiniciar).pack(side="left", padx=3)
        tk.Button(barra, text="Salto rápido (+200)", command=self.salto).pack(side="left", padx=3)

        tk.Label(barra, text="   Velocidad (" + UNIDAD + " simuladas/seg):").pack(side="left")
        self.vel = tk.Scale(barra, from_=0.5, to=30, resolution=0.5,
                            orient="horizontal", length=150)
        self.vel.set(VELOCIDAD_INICIAL)
        self.vel.pack(side="left")

        if SERVIDORES_EDITABLES:
            tk.Label(barra, text="   " + NOMBRE_SERVIDOR + "s:").pack(side="left")
            self.var_c = tk.IntVar(value=self.c)
            tk.Spinbox(barra, from_=1, to=8, width=3, textvariable=self.var_c,
                       command=self.cambiar_servidores).pack(side="left")

        # Lienzo de animación
        self.lienzo = tk.Canvas(raiz, width=ANCHO, height=ALTO,
                                bg=COLOR_FONDO, highlightthickness=0)
        self.lienzo.pack()

        # Panel inferior
        inferior = tk.Frame(raiz)
        inferior.pack(fill="x")

        self.stats = tk.Label(inferior, justify="left", anchor="nw",
                              font=("Consolas", 10), width=44, padx=10, pady=6)
        self.stats.pack(side="left", fill="y")

        self.graf = tk.Canvas(inferior, width=700, height=235,
                              bg="white", highlightthickness=0)
        self.graf.pack(side="left")

        self.tick()

    # -------------------------------------------------------------------------
    # Controles
    # -------------------------------------------------------------------------
    def alternar(self):
        """Inicia o pausa la simulación."""
        self.corriendo = not self.corriendo
        self.btn.config(text="Pausar" if self.corriendo else "Iniciar")
        self.ultimo_tick = time.time()

    def reiniciar(self):
        """Reinicia completamente la simulación."""
        self.modelo = Modelo(self.c)
        self.corriendo = False
        self.btn.config(text="Iniciar")
        self.raiz.title(TITULO)

    def cambiar_servidores(self):
        """Cambia el número de servidores (si está habilitado)."""
        try:
            self.c = max(1, min(8, int(self.var_c.get())))
        except ValueError:
            return
        self.reiniciar()

    def salto(self):
        """Avanza rápidamente la simulación respetando la condición de terminación."""
        m = self.modelo
        objetivo = m.n_salidas + 200

        while True:
            if m.terminado():
                break
            if m.n_salidas >= objetivo and m.n_salidas >= MAX_ATENDIDOS:
                if len(m.cola) == 0 and m.ocupados() == 0:
                    break
            m.avanzar(m.t + 50.0 / TASA_LLEGADA)

        m.salieron.clear()
        m.muestrear()

        if m.terminado():
            self.corriendo = False
            self.btn.config(text="Iniciar")
            self.raiz.title(TITULO + "  |  TERMINADA (%d atendidos)" % m.n_salidas)

    # -------------------------------------------------------------------------
    # Ciclo de animación
    # -------------------------------------------------------------------------
    def tick(self):
        ahora = time.time()
        dt = min(ahora - self.ultimo_tick, 0.1)
        self.ultimo_tick = ahora

        if self.corriendo:
            m = self.modelo

            if m.terminado():
                self.corriendo = False
                self.btn.config(text="Iniciar")
                self.raiz.title(TITULO + "  |  TERMINADA (%d atendidos)" % m.n_salidas)
            else:
                m.avanzar(m.t + dt * self.vel.get())
                self.contador += 1
                if self.contador % 3 == 0:
                    m.muestrear()

        self.dibujar()
        self.raiz.after(30, self.tick)

    # -------------------------------------------------------------------------
    # Dibujo
    # -------------------------------------------------------------------------
    def geometria_servidor(self, k):
        c = self.modelo.c
        arriba, abajo = 40, ALTO - 25
        hueco = (abajo - arriba) / c
        alto = min(72, hueco - 16)
        yc = arriba + hueco * (k + 0.5)
        return SERV_X, yc - alto / 2, CAJA_ANCHO, alto

    def caja3d(self, x, y, w, h, color, prof=16):
        cv = self.lienzo
        d = prof * 0.7
        cv.create_polygon(x, y, x + prof, y - d, x + w + prof, y - d, x + w, y,
                          fill=escalar(color, 1.15), outline="#444")
        cv.create_polygon(x + w, y, x + w + prof, y - d,
                          x + w + prof, y + h - d, x + w, y + h,
                          fill=escalar(color, 0.75), outline="#444")
        cv.create_rectangle(x, y, x + w, y + h, fill=color, outline="#444")

    def dibujar_cliente(self, x, y, color):
        cv = self.lienzo
        if FORMA_CLIENTE == "barco":
            cv.create_polygon(x - 17, y - 2, x + 17, y - 2,
                              x + 10, y + 9, x - 10, y + 9,
                              fill=color, outline="#333")
            cv.create_line(x, y - 2, x, y - 24, fill="#333", width=2)
            cv.create_polygon(x + 1, y - 23, x + 14, y - 5, x + 1, y - 5,
                              fill="white", outline="#333")
        else:
            cv.create_oval(x - 5, y - 17, x + 5, y - 7, fill="#f1c9a5", outline="#333")
            cv.create_oval(x - 9, y - 7, x + 9, y + 12, fill=color, outline="#333")

    def mover(self, cli, tx, ty):
        if cli.x is None:
            cli.x, cli.y = X_ENTRADA, ALTO / 2
        cli.x += (tx - cli.x) * 0.22
        cli.y += (ty - cli.y) * 0.22

    def dibujar(self):
        cv = self.lienzo
        m = self.modelo
        cv.delete("all")

        cv.create_text(X_ENTRADA - 20, 16, text="Llegadas", anchor="w",
                       font=("Segoe UI", 10, "bold"))
        cv.create_text(X_COLA_CABEZA - MAX_VISIBLES * SEP / 2 + 20, 16,
                       text="Fila de espera (orden de llegada)",
                       font=("Segoe UI", 10, "bold"))
        cv.create_text(SERV_X, 16,
                       text=NOMBRE_SERVIDOR + ("s" if m.c > 1 else ""),
                       anchor="w", font=("Segoe UI", 10, "bold"))

        cv.create_rectangle(X_ENTRADA - 25, ALTO / 2 - 24,
                            X_COLA_CABEZA + 25, ALTO / 2 + 24,
                            fill=COLOR_CARRIL, outline="")

        for k in range(m.c):
            x, y, w, h = self.geometria_servidor(k)
            ocupado = m.servidores[k] is not None
            self.caja3d(x, y, w, h,
                        COLOR_SERV_OCUPADO if ocupado else COLOR_SERV_LIBRE)
            nombre = NOMBRE_SERVIDOR + (" " + str(k + 1) if m.c > 1 else "")
            cv.create_text(x + 8, y + 12, text=nombre, anchor="w",
                           font=("Segoe UI", 9, "bold"))
            cv.create_text(x + 8, y + h - 12,
                           text="ocupado" if ocupado else "libre",
                           anchor="w", font=("Segoe UI", 8))

        ref = 3.0 / TASA_SERVICIO
        for i, cli in enumerate(m.cola[:MAX_VISIBLES]):
            self.mover(cli, X_COLA_CABEZA - i * SEP, ALTO / 2)
        for cli in m.cola[MAX_VISIBLES:]:
            self.mover(cli, X_COLA_CABEZA - (MAX_VISIBLES - 1) * SEP, ALTO / 2)

        for i, cli in enumerate(m.cola[:MAX_VISIBLES]):
            f = min(1.0, (m.t - cli.llegada) / ref)
            self.dibujar_cliente(cli.x, cli.y, mezclar(VERDE, ROJO, f))

        if len(m.cola) > MAX_VISIBLES:
            cv.create_text(X_COLA_CABEZA - (MAX_VISIBLES - 1) * SEP - 10,
                           ALTO / 2 + 38,
                           text="+" + str(len(m.cola) - MAX_VISIBLES) + " más",
                           font=("Segoe UI", 10, "bold"), fill=ROJO)

        for k, cli in enumerate(m.servidores):
            if cli is not None:
                x, y, w, h = self.geometria_servidor(k)
                self.mover(cli, x + w * 0.74, y + h / 2)
                self.dibujar_cliente(cli.x, cli.y, AZUL)

        quedan = []
        for cli in m.salieron:
            if cli.x is None:
                continue
            self.mover(cli, ANCHO + 80, cli.y)
            if cli.x < ANCHO + 20:
                self.dibujar_cliente(cli.x, cli.y, "#9aa0a6")
                quedan.append(cli)
        m.salieron = quedan

        cv.create_text(ANCHO - 10, ALTO - 10, anchor="e",
                       font=("Segoe UI", 10),
                       text="t = %.1f %s" % (m.t, UNIDAD))

        self.dibujar_estadisticas()
        self.dibujar_graficas()

    def dibujar_estadisticas(self):
        """Actualiza el panel de estadísticas."""
        m = self.modelo
        teo = erlang_c(m.c, TASA_LLEGADA, TASA_SERVICIO)
        t = m.t

        wq = m.wq()
        w = (m.suma_espera + m.suma_servicio) / m.n_inicio if m.n_inicio else 0.0
        lq = m.area_cola / t if t > 0 else 0.0
        rho = m.area_ocupados / (m.c * t) if t > 0 else 0.0

        def teoria(k):
            return "%9.3f" % teo[k] if teo else "  inestable"

        lineas = [
            "%s llegados: %d   atendidos: %d" % (
                NOMBRE_CLIENTES.capitalize(), m.n_llegadas, m.n_salidas),
            "En fila: %d   En servicio: %d" % (len(m.cola), m.ocupados()),
            "lambda = %.2f   mu = %.2f  (por %s)" % (
                TASA_LLEGADA, TASA_SERVICIO,
                UNIDAD[:-1] if UNIDAD.endswith("s") else UNIDAD),
            "",
            "%-22s %9s %10s" % ("Medida", "Simulación", "Teoría"),
            "%-22s %9.3f %10s" % ("Wq espera en fila", wq, teoria("Wq")),
            "%-22s %9.3f %10s" % ("W  tiempo en sistema", w, teoria("W")),
            "%-22s %9.3f %10s" % ("Lq en fila (prom.)", lq, teoria("Lq")),
            "%-22s %9.3f %10s" % ("rho utilización", rho, teoria("rho")),
            "",
            "Unidad de tiempo: " + UNIDAD,
        ]

        if teo is None:
            lineas.append("rho >= 1: la fila crece sin límite")

        # Mensaje de terminación con la cantidad REAL de atendidos
        if m.terminado():
            lineas.append("")
            lineas.append("*** SIMULACIÓN TERMINADA ***")
            lineas.append("(sistema vacío - %d atendidos)" % m.n_salidas)

        self.stats.config(text="\n".join(lineas))

    def graficar(self, x0, y0, x1, y1, datos, titulo, color, ref=None, entero=False):
        g = self.graf
        g.create_text((x0 + x1) / 2, y0 - 16, text=titulo,
                      font=("Segoe UI", 9, "bold"))
        g.create_rectangle(x0, y0, x1, y1, outline="#888")

        ymax = max(max(datos) if datos else 0.0,
                   ref * 1.2 if ref else 0.0,
                   1.0 if entero else 1e-6)
        if entero:
            ymax = 4 * math.ceil(ymax / 1.1 / 4)
        else:
            ymax *= 1.1

        for i in range(5):
            y = y1 - (y1 - y0) * i / 4
            valor = ymax * i / 4
            g.create_line(x0, y, x1, y, fill="#e4e4e4")
            g.create_text(x0 - 4, y,
                          text=("%.0f" % valor) if entero else ("%.2f" % valor),
                          anchor="e", font=("Segoe UI", 8))

        if len(datos) > 1:
            puntos = []
            n = len(datos)
            for i, v in enumerate(datos):
                puntos.append(x0 + (x1 - x0) * i / (n - 1))
                puntos.append(y1 - (y1 - y0) * v / ymax)
            g.create_line(*puntos, fill=color, width=2)

        if ref is not None:
            yr = y1 - (y1 - y0) * ref / ymax
            g.create_line(x0, yr, x1, yr, fill=ROJO, dash=(6, 4), width=2)
            g.create_text(x1 - 4, yr - 8, text="teoría", fill=ROJO,
                          anchor="e", font=("Segoe UI", 8))

    def dibujar_graficas(self):
        g = self.graf
        g.delete("all")
        m = self.modelo
        hist = list(m.historial)
        teo = erlang_c(m.c, TASA_LLEGADA, TASA_SERVICIO)

        self.graficar(45, 40, 335, 190,
                      [h[1] for h in hist],
                      "Clientes en la fila", AZUL, entero=True)

        self.graficar(395, 40, 685, 190,
                      [h[2] for h in hist],
                      "Espera promedio Wq acumulada (" + UNIDAD + ")",
                      "#e08a1e",
                      ref=teo["Wq"] if teo else None)

        if hist:
            texto = "tiempo simulado: %.1f a %.1f %s" % (
                hist[0][0], hist[-1][0], UNIDAD)
            g.create_text(350, 215, text=texto,
                          font=("Segoe UI", 8), fill="#555")


# =============================================================================
# PUNTO DE ENTRADA
# =============================================================================
if __name__ == "__main__":
    if SEMILLA is not None:
        random.seed(SEMILLA)

    raiz = tk.Tk()
    App(raiz)
    raiz.mainloop()