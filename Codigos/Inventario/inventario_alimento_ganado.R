# INVENTARIO - política (Q, R)          Tema 4.3.2 Problemas con sistemas de inventarios
# Ejemplo: FORRAJERÍA (tienda de alimento para ganado)
#
#  Producto: sacos de alimento balanceado de 40 kg.
#  Demanda diaria (sacos): 10, 15, 20, 25, 30   con prob. 0.10, 0.20, 0.40, 0.20, 0.10
#  Tiempo de entrega (días): 1, 2, 3            con prob. 0.30, 0.40, 0.30
#  Política (Q, R): al cerrar el día, si inventario <= R y no hay pedido pendiente, se piden Q sacos.
#  Venta perdida si no hay existencias.
#  Costos: pedido (flete) = $100, mantener = $0.10 por saco por día, faltante = $5 por saco perdido.
#
# Se simulan 365 días con 30 réplicas por política y se busca la combinación (Q, R) de menor costo.
#
# Ejecutar: Rscript inventario_alimento_ganado.R   (o abrir en RStudio y presionar Source)

# ---------------- Parámetros ----------------
dem_valores <- c(10, 15, 20, 25, 30)
dem_probs   <- c(0.10, 0.20, 0.40, 0.20, 0.10)
lt_valores  <- c(1, 2, 3)
lt_probs    <- c(0.30, 0.40, 0.30)

costo_pedido   <- 100
costo_mantener <- 0.10
costo_faltante <- 5

dias        <- 365
inv_inicial <- 60
replicas    <- 30
q_opciones  <- c(100, 150, 200, 250)
r_opciones  <- c(40, 50, 60, 70, 80)
semilla <- NULL   # NULL = cada ejecución distinta; pon un número (ej. 42) para repetir los mismos resultados
if (!is.null(semilla)) set.seed(semilla)

# Transformada inversa para variable discreta
discreta <- function(valores, probs) {
  u <- runif(1)
  valores[which(u < cumsum(probs))[1]]
}

simular_anio <- function(Q, R) {
  inv <- inv_inicial
  pendiente <- FALSE
  dia_llegada <- -1
  c_pedidos <- c_mant <- c_falt <- 0
  n_pedidos <- perdidas <- suma_inv <- 0
  
  for (dia in 1:dias) {
    # 1) llega el pedido
    if (pendiente && dia == dia_llegada) {
      inv <- inv + Q
      pendiente <- FALSE
    }
    # 2) demanda del día
    d <- discreta(dem_valores, dem_probs)
    vendido  <- min(inv, d)
    faltante <- d - vendido
    inv <- inv - vendido
    perdidas <- perdidas + faltante
    c_falt   <- c_falt + costo_faltante * faltante
    # 3) cierre del día
    c_mant   <- c_mant + costo_mantener * inv
    suma_inv <- suma_inv + inv
    if (!pendiente && inv <= R) {
      pendiente <- TRUE
      dia_llegada <- dia + discreta(lt_valores, lt_probs)
      n_pedidos <- n_pedidos + 1
      c_pedidos <- c_pedidos + costo_pedido
    }
  }
  c(costo = c_pedidos + c_mant + c_falt, pedidos = n_pedidos, perdidas = perdidas,
    inv_prom = suma_inv / dias, c_pedidos = c_pedidos, c_mant = c_mant, c_falt = c_falt)
}

promedio <- function(Q, R) rowMeans(replicate(replicas, simular_anio(Q, R)))

cat("=== INVENTARIO (Q, R) - Forrajería (sacos de alimento para ganado) ===\n")
cat(sprintf("%d días por corrida, %d réplicas por política\n\n", dias, replicas))

costos <- matrix(NA, nrow = length(q_opciones), ncol = length(r_opciones),
                 dimnames = list(paste0("Q=", q_opciones), paste0("R=", r_opciones)))
detalle <- list()
for (i in seq_along(q_opciones)) {
  for (j in seq_along(r_opciones)) {
    r <- promedio(q_opciones[i], r_opciones[j])
    costos[i, j] <- r["costo"]
    detalle[[paste(i, j)]] <- r
  }
}
cat("Costo anual promedio ($) por política:\n")
print(round(costos))

pos <- which(costos == min(costos), arr.ind = TRUE)[1, ]
mejor <- detalle[[paste(pos[1], pos[2])]]
cat(sprintf("\nMejor política: Q = %d sacos, R = %d sacos\n", q_opciones[pos[1]], r_opciones[pos[2]]))
cat(sprintf("  Costo total anual:       $%.2f\n", mejor["costo"]))
cat(sprintf("    - Pedidos:             $%.2f  (%.1f pedidos/año)\n", mejor["c_pedidos"], mejor["pedidos"]))
cat(sprintf("    - Mantener inventario: $%.2f  (inventario promedio %.1f sacos)\n", mejor["c_mant"], mejor["inv_prom"]))
cat(sprintf("    - Faltantes:           $%.2f  (%.1f sacos perdidos/año)\n", mejor["c_falt"], mejor["perdidas"]))

# ---------------- GRÁFICA EN 2D (Inventario diario con eventos) ----------------
simular_anio_detalle <- function(Q, R) {
  inv <- inv_inicial
  pendiente <- FALSE
  dia_llegada <- -1
  hist_inv <- numeric(dias)
  dias_pedido <- integer(0)
  dias_llegada_v <- integer(0)
  dias_faltante <- integer(0)
  
  for (dia in 1:dias) {
    if (pendiente && dia == dia_llegada) {
      inv <- inv + Q
      pendiente <- FALSE
      dias_llegada_v <- c(dias_llegada_v, dia)
    }
    d <- discreta(dem_valores, dem_probs)
    vendido  <- min(inv, d)
    faltante <- d - vendido
    inv <- inv - vendido
    if (faltante > 0) dias_faltante <- c(dias_faltante, dia)
    hist_inv[dia] <- inv
    if (!pendiente && inv <= R) {
      pendiente <- TRUE
      dia_llegada <- dia + discreta(lt_valores, lt_probs)
      dias_pedido <- c(dias_pedido, dia)
    }
  }
  list(hist_inv = hist_inv, dias_pedido = dias_pedido, dias_llegada = dias_llegada_v, dias_faltante = dias_faltante)
}

Qm <- q_opciones[pos[1]]
Rm <- r_opciones[pos[2]]
detalle_grafica <- simular_anio_detalle(Qm, Rm)

par(mar = c(4.5, 4.5, 3, 1))
plot(1:dias, detalle_grafica$hist_inv, type = "l", col = "royalblue", lwd = 1.8,
     xlab = "día del año", ylab = "",
     main = "Inventario al cierre de cada día",
     ylim = c(0, max(detalle_grafica$hist_inv, Qm + Rm) * 1.15))

abline(h = Rm, col = "red", lty = 2, lwd = 1.5)

if (length(detalle_grafica$dias_pedido) > 0) {
  points(detalle_grafica\(dias_pedido, detalle_grafica\)hist_inv[detalle_grafica$dias_pedido], pch = 18, col = "darkorange", cex = 1.2)
}
if (length(detalle_grafica$dias_llegada) > 0) {
  points(detalle_grafica\(dias_llegada, detalle_grafica\)hist_inv[detalle_grafica$dias_llegada], pch = 17, col = "forestgreen", cex = 1.1)
}
if (length(detalle_grafica$dias_faltante) > 0) {
  points(detalle_grafica\(dias_faltante, rep(0, length(detalle_grafica\)dias_faltante)), pch = 16, col = "firebrick", cex = 1.1)
}

legend("topleft", legend = c("se hace un pedido", "llega el pedido", "día con faltante (venta perdida)"),
       col = c("darkorange", "forestgreen", "firebrick"), pch = c(18, 17, 16),
       bty = "n", cex = 0.75, horiz = TRUE)

