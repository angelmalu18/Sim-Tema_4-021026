# -*- coding: utf-8 -*-
"""
INVENTARIO - política (Q, R)          Tema 4.3.2 Problemas con sistemas de inventarios
Ejemplo: FORRAJERÍA (tienda de alimento para ganado)

  Producto: sacos de alimento balanceado de 40 kg.
  Demanda diaria (sacos): 10, 15, 20, 25, 30   con prob. 0.10, 0.20, 0.40, 0.20, 0.10
  Tiempo de entrega del proveedor (días): 1, 2, 3   con prob. 0.30, 0.40, 0.30
  Política (Q, R): al cerrar el día, si el inventario <= R y no hay pedido pendiente, se pide Q sacos.
  Si no hay existencias, la venta se pierde.
  Costos: pedido (flete) = $100, mantener = $0.10 por saco por día, faltante = $5 por saco perdido.

INTERFAZ GRÁFICA (Python + tkinter, no necesita instalar nada extra). Tiene dos pestañas:
  1) Simulación animada de un año: bodega en 3D que se llena y se vacía, camión de reabastecimiento,
     gráfica del inventario día por día y costos acumulados.
  2) Optimización en 3D: barras 3D con el costo anual de cada política (Q, R). Se rotan con el mouse
     (arrastrar) y se acercan con la rueda. La barra dorada es la política de menor costo.
Ejecutar:  python inventario_alimento_ganado_grafico.py
"""
import math
import random
import time
import tkinter as tk
from tkinter import ttk

# ==================== PARÁMETROS DEL EJEMPLO ====================
TITULO = "INVENTARIO (Q, R)  |  Forrajería: sacos de alimento para ganado"
DEM_VALORES = [10, 15, 20, 25, 30]
DEM_PROBS = [0.10, 0.20, 0.40, 0.20, 0.10]
LT_VALORES = [1, 2, 3]
LT_PROBS = [0.30, 0.40, 0.30]

COSTO_PEDIDO = 100.0
COSTO_MANTENER = 0.10
COSTO_FALTANTE = 5.0

DIAS = 365
INV_INICIAL = 60
REPLICAS = 30
Q_OPCIONES = [100, 150, 200, 250]
R_OPCIONES = [40, 50, 60, 70, 80]
Q_INICIAL = 200
R_INICIAL = 50
SEMILLA = None      # None = cada ejecución distinta; pon un número (ej. 2026) para repetir los mismos resultados


# ==================== VARIABLES ALEATORIAS ====================
def discreta(valores, probs):
    """Transformada inversa para una variable discreta."""
    u = random.random()
    acum = 0.0
    for v, p in zip(valores, probs):
        acum += p
        if u < acum:
            return v
    return valores[-1]


# ==================== COLORES ====================
def a_rgb(color):
    return int(color[1:3], 16), int(color[3:5], 16), int(color[5:7], 16)


def a_hex(r, g, b):
    return "#%02x%02x%02x" % (max(0, min(255, int(r))), max(0, min(255, int(g))), max(0, min(255, int(b))))


def mezclar(c1, c2, f):
    f = max(0.0, min(1.0, f))
    r1, g1, b1 = a_rgb(c1)
    r2, g2, b2 = a_rgb(c2)
    return a_hex(r1 + (r2 - r1) * f, g1 + (g2 - g1) * f, b1 + (b2 - b1) * f)


def escalar(color, factor):
    r, g, b = a_rgb(color)
    return a_hex(r * factor, g * factor, b * factor)


VERDE = "#2e9e4f"
ROJO = "#d33a2c"
NARANJA = "#e39a4a"
AZUL = "#3b7dd8"
ORO = "#f2c200"


# ==================== MODELO: un año de inventario ====================
class Anio:
    def __init__(self, Q, R):
        self.Q = Q
        self.R = R
        self.reiniciar()

    def reiniciar(self):
        self.dia = 0
        self.inv = INV_INICIAL
        self.pendiente = False
        self.dia_pedido = -1
        self.dia_llegada = -1
        self.c_ped = self.c_mant = self.c_falt = 0.0
        self.n_ped = self.perdidas = self.suma_inv = 0
        self.demanda_hoy = 0
        self.faltante_hoy = 0
        self.historial = [INV_INICIAL]      # inventario al cierre de cada día (índice = día)
        self.eventos = []                   # (día, tipo)

    def paso(self, guardar=True):
        """Simula un día."""
        self.dia += 1
        # 1) llega el pedido (si toca hoy)
        if self.pendiente and self.dia == self.dia_llegada:
            self.inv += self.Q
            self.pendiente = False
            if guardar:
                self.eventos.append((self.dia, "llegada"))
        # 2) demanda del día
        d = discreta(DEM_VALORES, DEM_PROBS)
        vendido = min(self.inv, d)
        falt = d - vendido
        self.inv -= vendido
        self.demanda_hoy = d
        self.faltante_hoy = falt
        self.perdidas += falt
        self.c_falt += COSTO_FALTANTE * falt
        if falt > 0 and guardar:
            self.eventos.append((self.dia, "faltante"))
        # 3) cierre del día: costo de mantener y revisión de la política
        self.c_mant += COSTO_MANTENER * self.inv
        self.suma_inv += self.inv
        if not self.pendiente and self.inv <= self.R:
            self.pendiente = True
            self.dia_pedido = self.dia
            self.dia_llegada = self.dia + discreta(LT_VALORES, LT_PROBS)
            self.n_ped += 1
            self.c_ped += COSTO_PEDIDO
            if guardar:
                self.eventos.append((self.dia, "pedido"))
        if guardar:
            self.historial.append(self.inv)

    def costo(self):
        return self.c_ped + self.c_mant + self.c_falt


def promedio(Q, R):
    """Promedio de REPLICAS años simulados con la política (Q, R)."""
    tot = {"costo": 0.0, "pedidos": 0.0, "perdidas": 0.0, "inv_prom": 0.0,
           "c_ped": 0.0, "c_mant": 0.0, "c_falt": 0.0}
    for _ in range(REPLICAS):
        a = Anio(Q, R)
        for _ in range(DIAS):
            a.paso(guardar=False)
        tot["costo"] += a.costo()
        tot["pedidos"] += a.n_ped
        tot["perdidas"] += a.perdidas
        tot["inv_prom"] += a.suma_inv / DIAS
        tot["c_ped"] += a.c_ped
        tot["c_mant"] += a.c_mant
        tot["c_falt"] += a.c_falt
    return {k: v / REPLICAS for k, v in tot.items()}


# ==================== INTERFAZ ====================
class App:
    def __init__(self, raiz):
        self.raiz = raiz
        raiz.title(TITULO)
        raiz.resizable(False, False)
        self.corriendo = False
        self.acum = 0.0
        self.ultimo_tick = time.time()
        # vista 3D
        self.az = -0.75
        self.el = 0.62
        self.esc = 88.0
        self.costos = {}
        self.detalle = {}
        self.pendientes = []
        self.mejor = None
        self.arrastre = None

        self.nb = ttk.Notebook(raiz)
        self.nb.pack()
        self.tab1 = tk.Frame(self.nb)
        self.tab2 = tk.Frame(self.nb)
        self.nb.add(self.tab1, text="  1) Simulación de un año (animada)  ")
        self.nb.add(self.tab2, text="  2) Optimización 3D: costo según Q y R  ")
        self.construir_tab1()
        self.construir_tab2()
        self.anio = Anio(Q_INICIAL, R_INICIAL)
        self.reiniciar()
        self.tick()

    # ---------------- pestaña 1 ----------------
    def construir_tab1(self):
        f = self.tab1
        barra = tk.Frame(f, padx=8, pady=6)
        barra.pack(fill="x")
        self.btn = tk.Button(barra, text="Iniciar", width=9, command=self.alternar)
        self.btn.pack(side="left", padx=3)
        tk.Button(barra, text="Reiniciar", width=9, command=self.reiniciar).pack(side="left", padx=3)
        tk.Button(barra, text="Año completo (instantáneo)", command=self.anio_completo).pack(side="left", padx=3)
        tk.Label(barra, text="  Q (sacos por pedido):").pack(side="left")
        self.var_q = tk.IntVar(value=Q_INICIAL)
        sq = tk.Spinbox(barra, from_=50, to=400, increment=10, width=5, textvariable=self.var_q,
                        command=self.reiniciar)
        sq.pack(side="left")
        sq.bind("<Return>", lambda e: self.reiniciar())
        tk.Label(barra, text="  R (punto de reorden):").pack(side="left")
        self.var_r = tk.IntVar(value=R_INICIAL)
        sr = tk.Spinbox(barra, from_=0, to=200, increment=5, width=5, textvariable=self.var_r,
                        command=self.reiniciar)
        sr.pack(side="left")
        sr.bind("<Return>", lambda e: self.reiniciar())
        tk.Label(barra, text="  Velocidad (días/seg):").pack(side="left")
        self.vel = tk.Scale(barra, from_=1, to=120, resolution=1, orient="horizontal", length=130)
        self.vel.set(25)
        self.vel.pack(side="left")
        self.c1 = tk.Canvas(f, width=1000, height=410, bg="#f6f7f9", highlightthickness=0)
        self.c1.pack()
        self.info1 = tk.Label(f, justify="left", anchor="w", font=("Consolas", 10), padx=10, pady=6)
        self.info1.pack(fill="x")

    def leer_qr(self):
        try:
            q = max(1, int(self.var_q.get()))
            r = max(0, int(self.var_r.get()))
        except (ValueError, tk.TclError):
            q, r = self.anio.Q, self.anio.R
        return q, r

    def alternar(self):
        if self.anio.dia >= DIAS:
            self.reiniciar()
        self.corriendo = not self.corriendo
        self.btn.config(text="Pausar" if self.corriendo else "Iniciar")
        self.ultimo_tick = time.time()

    def reiniciar(self):
        q, r = self.leer_qr()
        self.anio = Anio(q, r)
        self.capacidad = max(50, int(math.ceil(max(INV_INICIAL, q + r) * 1.1 / 50.0)) * 50)
        self.corriendo = False
        self.acum = 0.0
        self.btn.config(text="Iniciar")

    def anio_completo(self):
        q, r = self.leer_qr()
        if self.anio.dia >= DIAS or (self.anio.Q, self.anio.R) != (q, r):
            self.reiniciar()
        while self.anio.dia < DIAS:
            self.anio.paso()
        self.corriendo = False
        self.btn.config(text="Iniciar")

    def caja3d(self, cv, x, y, w, h, color, prof):
        d = prof * 0.7
        cv.create_polygon(x, y, x + prof, y - d, x + w + prof, y - d, x + w, y,
                          fill=escalar(color, 1.15), outline="#444")
        cv.create_polygon(x + w, y, x + w + prof, y - d, x + w + prof, y + h - d, x + w, y + h,
                          fill=escalar(color, 0.75), outline="#444")
        cv.create_rectangle(x, y, x + w, y + h, fill=color, outline="#444")

    def camion(self, cv, x, y):
        cv.create_rectangle(x, y - 24, x + 52, y, fill=NARANJA, outline="#333")
        cv.create_text(x + 26, y - 12, text="Q=%d" % self.anio.Q, font=("Segoe UI", 8, "bold"))
        cv.create_polygon(x + 52, y - 17, x + 66, y - 17, x + 74, y - 8, x + 74, y, x + 52, y,
                          fill="#c96a1a", outline="#333")
        cv.create_oval(x + 8, y - 6, x + 22, y + 8, fill="#333")
        cv.create_oval(x + 52, y - 6, x + 66, y + 8, fill="#333")

    def dibujar1(self):
        cv = self.c1
        a = self.anio
        cv.delete("all")
        negrita = ("Segoe UI", 10, "bold")
        # ---- bodega en 3D ----
        x0, ytop, w, H, prof = 45, 75, 150, 220, 34
        d = prof * 0.7
        ybase = ytop + H
        cap = self.capacidad
        cv.create_text(x0 + w / 2 + 15, 30, text="Bodega (sacos)", font=negrita)
        cv.create_rectangle(x0, ytop, x0 + w, ybase, outline="#999")
        cv.create_line(x0, ytop, x0 + prof, ytop - d, fill="#999")
        cv.create_line(x0 + w, ytop, x0 + w + prof, ytop - d, fill="#999")
        cv.create_line(x0 + prof, ytop - d, x0 + w + prof, ytop - d, fill="#999")
        cv.create_line(x0 + w + prof, ytop - d, x0 + w + prof, ybase - d, fill="#999")
        cv.create_line(x0 + w, ybase, x0 + w + prof, ybase - d, fill="#999")
        nivel = H * min(a.inv, cap) / cap
        if nivel > 0:
            color = VERDE if a.inv > a.R else NARANJA
            self.caja3d(cv, x0, ybase - nivel, w, nivel, color, prof)
        yr = ybase - H * a.R / cap
        cv.create_line(x0 - 8, yr, x0 + w + prof + 8, yr, fill=ROJO, dash=(5, 3), width=2)
        cv.create_text(x0 - 12, yr, text="R", fill=ROJO, anchor="e", font=negrita)
        cv.create_text(x0 + w / 2 + 15, ybase + 16, text="Inventario: %d sacos" % a.inv, font=negrita)
        # ---- camión de reabastecimiento ----
        cv.create_line(30, 385, 250, 385, fill="#777", width=2)
        if a.pendiente:
            prog = (a.dia - a.dia_pedido) / float(max(1, a.dia_llegada - a.dia_pedido))
            self.camion(cv, 40 + prog * 110, 379)
            faltan = a.dia_llegada - a.dia
            cv.create_text(x0, 340, anchor="w", font=("Segoe UI", 9),
                           text="Pedido en camino: llega en %d día%s" % (faltan, "" if faltan == 1 else "s"))
        else:
            cv.create_text(x0, 340, anchor="w", font=("Segoe UI", 9), text="Sin pedido pendiente")
        # ---- gráfica del inventario ----
        px0, py0, px1, py1 = 310, 60, 975, 340
        ymax = float(cap)
        cv.create_text((px0 + px1) / 2, 22, text="Inventario al cierre de cada día", font=negrita)
        cv.create_rectangle(px0, py0, px1, py1, outline="#888", fill="white")
        for i in range(5):
            y = py1 - (py1 - py0) * i / 4.0
            cv.create_line(px0, y, px1, y, fill="#e6e6e6")
            cv.create_text(px0 - 5, y, text="%d" % (ymax * i / 4.0), anchor="e", font=("Segoe UI", 8))
        for dia in range(0, DIAS + 1, 50):
            x = px0 + (px1 - px0) * dia / float(DIAS)
            cv.create_line(x, py1, x, py1 + 4, fill="#888")
            cv.create_text(x, py1 + 14, text=str(dia), font=("Segoe UI", 8))
        cv.create_text((px0 + px1) / 2, py1 + 32, text="día del año", font=("Segoe UI", 9))
        yR = py1 - (py1 - py0) * a.R / ymax
        cv.create_line(px0, yR, px1, yR, fill=ROJO, dash=(6, 4), width=2)
        cv.create_text(px1 - 4, yR - 8, text="R = %d" % a.R, fill=ROJO, anchor="e", font=("Segoe UI", 8, "bold"))
        pts = []
        for dia, inv in enumerate(a.historial):
            pts.append(px0 + (px1 - px0) * dia / float(DIAS))
            pts.append(py1 - (py1 - py0) * min(inv, ymax) / ymax)
        if len(pts) >= 4:
            cv.create_line(*pts, fill=AZUL, width=2)
        for dia, tipo in a.eventos:
            x = px0 + (px1 - px0) * dia / float(DIAS)
            y = py1 - (py1 - py0) * min(a.historial[dia], ymax) / ymax
            if tipo == "pedido":
                cv.create_polygon(x, y - 6, x + 5, y, x, y + 6, x - 5, y, fill=NARANJA, outline="#333")
            elif tipo == "llegada":
                cv.create_polygon(x, y - 7, x + 6, y + 5, x - 6, y + 5, fill=VERDE, outline="#333")
            else:
                cv.create_oval(x - 4, py1 - 8, x + 4, py1 - 0, fill=ROJO, outline="#333")
        # leyenda
        lx = px0 + 10
        cv.create_polygon(lx, 44, lx + 5, 50, lx, 56, lx - 5, 50, fill=NARANJA, outline="#333")
        cv.create_text(lx + 10, 50, text="se hace un pedido", anchor="w", font=("Segoe UI", 8))
        cv.create_polygon(lx + 130, 43, lx + 136, 55, lx + 124, 55, fill=VERDE, outline="#333")
        cv.create_text(lx + 142, 50, text="llega el pedido", anchor="w", font=("Segoe UI", 8))
        cv.create_oval(lx + 240, 46, lx + 248, 54, fill=ROJO, outline="#333")
        cv.create_text(lx + 254, 50, text="día con faltante (venta perdida)", anchor="w", font=("Segoe UI", 8))
        if a.dia >= DIAS:
            cv.create_rectangle(px0 + 150, py0 + 90, px1 - 150, py0 + 150, fill="#fff8d6", outline="#b39b00")
            cv.create_text((px0 + px1) / 2, py0 + 112, text="Año completo", font=("Segoe UI", 12, "bold"))
            cv.create_text((px0 + px1) / 2, py0 + 134, text="Costo total del año: $%.2f" % a.costo(),
                           font=("Segoe UI", 11))
        prom = a.suma_inv / a.dia if a.dia else 0.0
        lineas = [
            "Día %3d / %d   Política (Q=%d, R=%d)   Demanda de hoy: %d sacos   Faltante de hoy: %d"
            % (a.dia, DIAS, a.Q, a.R, a.demanda_hoy, a.faltante_hoy),
            "Pedidos: $%9.2f (%d pedidos)   Mantener: $%9.2f (inv. prom. %.1f)   Faltantes: $%9.2f (%d sacos perdidos)"
            % (a.c_ped, a.n_ped, a.c_mant, prom, a.c_falt, a.perdidas),
            "COSTO TOTAL ACUMULADO: $%.2f" % a.costo(),
        ]
        self.info1.config(text="\n".join(lineas))

    # ---------------- pestaña 2 (3D) ----------------
    def construir_tab2(self):
        f = self.tab2
        barra = tk.Frame(f, padx=8, pady=6)
        barra.pack(fill="x")
        self.btn_calc = tk.Button(barra, text="Calcular malla (%d x %d políticas, %d réplicas c/u)"
                                  % (len(Q_OPCIONES), len(R_OPCIONES), REPLICAS), command=self.calcular_malla)
        self.btn_calc.pack(side="left", padx=3)
        self.var_auto = tk.BooleanVar(value=True)
        tk.Checkbutton(barra, text="Rotar automáticamente", variable=self.var_auto).pack(side="left", padx=10)
        self.btn_usar = tk.Button(barra, text="Usar la mejor política en la simulación animada",
                                  command=self.usar_mejor, state="disabled")
        self.btn_usar.pack(side="left", padx=3)
        self.c2 = tk.Canvas(f, width=1000, height=470, bg="white", highlightthickness=0)
        self.c2.pack()
        self.c2.bind("<ButtonPress-1>", self.al_presionar)
        self.c2.bind("<B1-Motion>", self.al_arrastrar)
        self.c2.bind("<MouseWheel>", self.al_rueda)
        self.c2.bind("<Button-4>", lambda e: self.zoom(1.08))
        self.c2.bind("<Button-5>", lambda e: self.zoom(0.92))
        self.info2 = tk.Label(f, justify="left", anchor="w", font=("Consolas", 10), padx=10, pady=6,
                              text="Pulsa \"Calcular malla\" para simular cada política (Q, R) y ver su costo anual en 3D.")
        self.info2.pack(fill="x")

    def al_presionar(self, e):
        self.arrastre = (e.x, e.y)

    def al_arrastrar(self, e):
        if self.arrastre:
            dx, dy = e.x - self.arrastre[0], e.y - self.arrastre[1]
            self.az += dx * 0.01
            self.el = max(0.08, min(1.5, self.el + dy * 0.01))
            self.arrastre = (e.x, e.y)

    def al_rueda(self, e):
        self.zoom(1.08 if e.delta > 0 else 0.92)

    def zoom(self, f):
        self.esc = max(40.0, min(200.0, self.esc * f))

    def calcular_malla(self):
        self.costos = {}
        self.detalle = {}
        self.mejor = None
        self.pendientes = [(i, j) for i in range(len(Q_OPCIONES)) for j in range(len(R_OPCIONES))]
        self.total_celdas = len(self.pendientes)
        self.btn_calc.config(state="disabled")
        self.btn_usar.config(state="disabled")
        self.paso_malla()

    def paso_malla(self):
        if not self.pendientes:
            self.btn_calc.config(state="normal")
            self.btn_usar.config(state="normal")
            qi, rj = self.mejor
            r = self.detalle[(qi, rj)]
            self.info2.config(text="\n".join([
                "Mejor política: Q = %d sacos, R = %d sacos   ->   costo anual promedio $%.2f"
                % (Q_OPCIONES[qi], R_OPCIONES[rj], r["costo"]),
                "Pedidos $%.2f (%.1f pedidos/año)   Mantener $%.2f (inv. prom. %.1f sacos)   "
                "Faltantes $%.2f (%.1f sacos perdidos/año)" % (r["c_ped"], r["pedidos"], r["c_mant"],
                                                              r["inv_prom"], r["c_falt"], r["perdidas"]),
                "Arrastra con el mouse para rotar; rueda para acercar. Altura: relativa al costo mínimo y máximo."]))
            return
        i, j = self.pendientes.pop(0)
        r = promedio(Q_OPCIONES[i], R_OPCIONES[j])
        self.costos[(i, j)] = r["costo"]
        self.detalle[(i, j)] = r
        if self.mejor is None or r["costo"] < self.costos[self.mejor]:
            self.mejor = (i, j)
        hechas = self.total_celdas - len(self.pendientes)
        self.info2.config(text="Calculando... %d de %d políticas" % (hechas, self.total_celdas))
        self.raiz.after(5, self.paso_malla)

    def usar_mejor(self):
        if self.mejor is None:
            return
        qi, rj = self.mejor
        self.var_q.set(Q_OPCIONES[qi])
        self.var_r.set(R_OPCIONES[rj])
        self.reiniciar()
        self.nb.select(0)

    def proyectar(self, x, y, z):
        ca, sa = math.cos(self.az), math.sin(self.az)
        ce, se = math.cos(self.el), math.sin(self.el)
        xr = x * ca - y * sa
        yr = x * sa + y * ca
        return 500 + xr * self.esc, 345 - (z * ce + yr * se) * self.esc, yr * ce - z * se

    def poligono(self, puntos, relleno, borde="#555", ancho=1):
        pr = [self.proyectar(*p) for p in puntos]
        plano = []
        for sx, sy, _ in pr:
            plano.extend([sx, sy])
        self.c2.create_polygon(*plano, fill=relleno, outline=borde, width=ancho)
        return sum(p[2] for p in pr) / len(pr)

    def texto3d(self, x, y, z, texto, **kw):
        sx, sy, _ = self.proyectar(x, y, z)
        self.c2.create_text(sx, sy, text=texto, **kw)

    def dibujar2(self):
        cv = self.c2
        cv.delete("all")
        nq, nr = len(Q_OPCIONES), len(R_OPCIONES)
        X = (nq - 1) / 2.0 + 0.7
        Y = (nr - 1) / 2.0 + 0.7
        # suelo y cuadrícula
        self.poligono([(-X, -Y, 0), (X, -Y, 0), (X, Y, 0), (-X, Y, 0)], "#eef1f4", "#aab")
        for i in range(nq + 1):
            x = -X + 0.2 + i * (2 * X - 0.4) / nq
            a = self.proyectar(x, -Y, 0)
            b = self.proyectar(x, Y, 0)
            cv.create_line(a[0], a[1], b[0], b[1], fill="#d5dae0")
        for j in range(nr + 1):
            y = -Y + 0.2 + j * (2 * Y - 0.4) / nr
            a = self.proyectar(-X, y, 0)
            b = self.proyectar(X, y, 0)
            cv.create_line(a[0], a[1], b[0], b[1], fill="#d5dae0")
        # eje vertical del costo
        a = self.proyectar(-X, -Y, 0)
        b = self.proyectar(-X, -Y, 2.7)
        cv.create_line(a[0], a[1], b[0], b[1], fill="#666", width=2, arrow="last")
        cv.create_text(b[0], b[1] - 12, text="costo anual ($)", font=("Segoe UI", 9, "bold"))
        # nombres de los ejes y valores
        self.texto3d(0, -Y - 0.75, 0, "Q = sacos por pedido", font=("Segoe UI", 10, "bold"), fill="#345")
        self.texto3d(-X - 0.95, 0, 0, "R = punto de reorden", font=("Segoe UI", 10, "bold"), fill="#345")
        for i, q in enumerate(Q_OPCIONES):
            self.texto3d(i - (nq - 1) / 2.0, -Y - 0.3, 0, str(q), font=("Segoe UI", 9))
        for j, r in enumerate(R_OPCIONES):
            self.texto3d(-X - 0.3, j - (nr - 1) / 2.0, 0, str(r), font=("Segoe UI", 9))
        # barras
        if self.costos:
            cmin, cmax = min(self.costos.values()), max(self.costos.values())
        else:
            cmin = cmax = 0.0
        barras = []
        for i in range(nq):
            for j in range(nr):
                x = i - (nq - 1) / 2.0
                y = j - (nr - 1) / 2.0
                if (i, j) in self.costos:
                    c = self.costos[(i, j)]
                    t = (c - cmin) / (cmax - cmin) if cmax > cmin else 0.0
                    h = 0.35 + 1.85 * t
                    color = ORO if (i, j) == self.mejor and not self.pendientes else mezclar(VERDE, ROJO, t)
                else:
                    c, h, color = None, 0.03, "#dfe3e8"
                barras.append((self.proyectar(x, y, 0)[2], x, y, h, color, c, (i, j)))
        barras.sort(key=lambda b: -b[0])
        for _, x, y, h, color, c, celda in barras:
            b = 0.36
            x0, x1, y0, y1 = x - b, x + b, y - b, y + b
            caras = [
                ([(x0, y0, h), (x1, y0, h), (x1, y1, h), (x0, y1, h)], 1.10),   # tapa
                ([(x0, y0, 0), (x1, y0, 0), (x1, y0, h), (x0, y0, h)], 0.95),
                ([(x0, y1, 0), (x1, y1, 0), (x1, y1, h), (x0, y1, h)], 0.75),
                ([(x0, y0, 0), (x0, y1, 0), (x0, y1, h), (x0, y0, h)], 0.85),
                ([(x1, y0, 0), (x1, y1, 0), (x1, y1, h), (x1, y0, h)], 0.70),
            ]
            caras.sort(key=lambda cf: -sum(self.proyectar(*p)[2] for p in cf[0]))
            for puntos, factor in caras:
                self.poligono(puntos, escalar(color, factor), "#333" if color == ORO else "#555",
                              2 if color == ORO else 1)
            if c is not None:
                self.texto3d(x, y, h + 0.14, "$%s" % format(int(round(c)), ","),
                             font=("Segoe UI", 8, "bold"), fill="#222")
        cv.create_text(20, 16, anchor="w", font=("Segoe UI", 9), fill="#555",
                       text="Arrastra para rotar  |  rueda del mouse: zoom  |  verde = menor costo, rojo = mayor, dorado = mejor política")

    # ---------------- ciclo de animación ----------------
    def tick(self):
        ahora = time.time()
        dt = min(ahora - self.ultimo_tick, 0.1)
        self.ultimo_tick = ahora
        if self.nb.index("current") == 0:
            if self.corriendo:
                self.acum += dt * self.vel.get()
                while self.acum >= 1 and self.anio.dia < DIAS:
                    self.anio.paso()
                    self.acum -= 1
                if self.anio.dia >= DIAS:
                    self.corriendo = False
                    self.btn.config(text="Iniciar")
            self.dibujar1()
        else:
            if self.var_auto.get() and not self.arrastre_activo():
                self.az += 0.012
            self.dibujar2()
        self.raiz.after(30, self.tick)

    def arrastre_activo(self):
        return False


if __name__ == "__main__":
    if SEMILLA is not None:
        random.seed(SEMILLA)
    raiz = tk.Tk()
    App(raiz)
    raiz.mainloop()
