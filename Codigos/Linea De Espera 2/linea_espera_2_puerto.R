# =============================================================================
# LÍNEA DE ESPERA 2 - MULTISERVIDOR (M/M/c)
# Ejemplo: MUELLES DE DESCARGA de un puerto pesquero
# (Versión R, solo consola, adaptada de linea_espera_2_puerto_grafico.py)
#
# Cómo ejecutar:
#     Rscript linea_espera_2_puerto.R
# o desde R / RStudio:
#     source("linea_espera_2_puerto.R")
#
# Condición de terminación: se han atendido al menos MAX_ATENDIDOS barcos,
# la fila está vacía y todos los muelles están libres.
# =============================================================================

# --- Configuración ------------------------------------------------------------
TASA_LLEGADA        <- 1.0   # lambda = barcos por hora
TASA_SERVICIO       <- 0.4   # mu por muelle = barcos por hora (2.5 h por barco)
SERVIDORES_INICIAL  <- 3     # número de muelles
MAX_ATENDIDOS       <- 800   # mínimo de barcos que deben ser atendidos
SEMILLA             <- NULL  # NULL = distinta cada vez; pon un número (ej. 7) para repetir
COMPARAR_MUELLES    <- 1:6   # tabla comparativa con distintos números de muelles
UNIDAD              <- "horas"

# --- Generador exponencial (transformada inversa) -----------------------------
exponencial <- function(tasa) -log(1 - runif(1)) / tasa

# --- Fórmulas teóricas M/M/c (Erlang C) ---------------------------------------
# Devuelve NULL si el sistema es inestable (rho >= 1)
erlang_c <- function(c, tasa_ll, tasa_sv) {
  a   <- tasa_ll / tasa_sv
  rho <- a / c
  if (rho >= 1) return(NULL)

  suma   <- sum(a^(0:(c - 1)) / factorial(0:(c - 1)))
  ultimo <- a^c / (factorial(c) * (1 - rho))
  prob_esperar <- ultimo / (suma + ultimo)

  lq <- prob_esperar * rho / (1 - rho)
  wq <- lq / tasa_ll
  list(Wq = wq, W = wq + 1 / tasa_sv, Lq = lq, rho = rho)
}

# --- Simulación de eventos discretos ------------------------------------------
simular <- function(c, max_atendidos = MAX_ATENDIDOS) {
  estable <- !is.null(erlang_c(c, TASA_LLEGADA, TASA_SERVICIO))

  ultimo <- 0
  sig_llegada <- exponencial(TASA_LLEGADA)
  ocupado <- rep(FALSE, c)          # estado de cada muelle
  fin     <- rep(Inf, c)            # instante de fin de servicio de cada muelle
  cola_ll <- numeric(0)             # instantes de llegada de los barcos en fila
  cola_sv <- numeric(0)             # duración de servicio de los barcos en fila

  n_llegadas <- 0; n_inicio <- 0; n_salidas <- 0
  suma_espera <- 0; suma_servicio <- 0
  area_cola <- 0; area_ocupados <- 0

  repeat {
    # Terminación: suficientes atendidos, fila vacía y muelles libres.
    # (Si rho >= 1 la fila nunca se vacía, así que se corta al llegar a la meta.)
    if (n_salidas >= max_atendidos &&
        (!estable || (length(cola_ll) == 0 && !any(ocupado)))) break

    k    <- which.min(fin)
    t_ev <- min(sig_llegada, fin[k])

    # Acumular áreas bajo Lq y utilización
    dt <- t_ev - ultimo
    area_cola     <- area_cola + length(cola_ll) * dt
    area_ocupados <- area_ocupados + sum(ocupado) * dt
    ultimo <- t_ev

    if (sig_llegada <= fin[k]) {
      # --- Llegada de un barco ---
      n_llegadas <- n_llegadas + 1
      sv    <- exponencial(TASA_SERVICIO)
      libre <- which(!ocupado)
      if (length(libre) > 0) {
        j <- libre[1]
        suma_servicio <- suma_servicio + sv
        n_inicio      <- n_inicio + 1
        ocupado[j]    <- TRUE
        fin[j]        <- t_ev + sv
      } else {
        cola_ll <- c(cola_ll, t_ev)
        cola_sv <- c(cola_sv, sv)
      }
      sig_llegada <- t_ev + exponencial(TASA_LLEGADA)
    } else {
      # --- Fin de servicio del muelle k ---
      n_salidas  <- n_salidas + 1
      ocupado[k] <- FALSE
      fin[k]     <- Inf
      if (length(cola_ll) > 0) {
        suma_espera   <- suma_espera + (t_ev - cola_ll[1])
        suma_servicio <- suma_servicio + cola_sv[1]
        n_inicio      <- n_inicio + 1
        ocupado[k]    <- TRUE
        fin[k]        <- t_ev + cola_sv[1]
        cola_ll <- cola_ll[-1]
        cola_sv <- cola_sv[-1]
      }
    }
  }

  list(c = c, t = ultimo, llegados = n_llegadas, atendidos = n_salidas,
       Wq  = if (n_inicio > 0) suma_espera / n_inicio else 0,
       W   = if (n_inicio > 0) (suma_espera + suma_servicio) / n_inicio else 0,
       Lq  = if (ultimo > 0) area_cola / ultimo else 0,
       rho = if (ultimo > 0) area_ocupados / (c * ultimo) else 0)
}

# --- Impresión de resultados --------------------------------------------------
fmt_teo <- function(teo, k) if (is.null(teo)) "inestable" else sprintf("%.3f", teo[[k]])

imprimir_resultado <- function(r) {
  teo <- erlang_c(r$c, TASA_LLEGADA, TASA_SERVICIO)
  cat("\n=== LÍNEA DE ESPERA M/M/c - Muelles de un puerto pesquero ===\n")
  cat(sprintf("lambda = %.2f   mu = %.2f  (por hora)   Muelles (c) = %d\n",
              TASA_LLEGADA, TASA_SERVICIO, r$c))
  cat(sprintf("Barcos llegados: %d   atendidos: %d   tiempo simulado: %.1f %s\n\n",
              r$llegados, r$atendidos, r$t, UNIDAD))
  cat(sprintf("%-22s %11s %11s\n", "Medida", "Simulación", "Teoría"))
  cat(sprintf("%-22s %11.3f %11s\n", "Wq espera en fila",    r$Wq,  fmt_teo(teo, "Wq")))
  cat(sprintf("%-22s %11.3f %11s\n", "W  tiempo en sistema", r$W,   fmt_teo(teo, "W")))
  cat(sprintf("%-22s %11.3f %11s\n", "Lq en fila (prom.)",   r$Lq,  fmt_teo(teo, "Lq")))
  cat(sprintf("%-22s %11.3f %11s\n", "rho utilización",      r$rho, fmt_teo(teo, "rho")))
  cat(sprintf("\nUnidad de tiempo: %s\n", UNIDAD))
  if (is.null(teo)) {
    cat("rho >= 1: la fila crece sin límite (se detuvo al llegar a los atendidos pedidos)\n")
  } else {
    cat("\n*** SIMULACIÓN TERMINADA ***\n")
    cat(sprintf("(sistema vacío - %d atendidos)\n", r$atendidos))
  }
}

# --- Programa principal -------------------------------------------------------
if (!is.null(SEMILLA)) set.seed(SEMILLA)

imprimir_resultado(simular(SERVIDORES_INICIAL))

# Tabla comparativa: cómo cambia el desempeño con el número de muelles
cat("\n=== Comparación según el número de muelles ===\n")
cat(sprintf("%-7s %10s %10s %10s %10s %10s\n",
            "Muelles", "Wq sim", "Wq teo", "Lq sim", "Lq teo", "rho sim"))
for (cc in COMPARAR_MUELLES) {
  r   <- simular(cc)
  teo <- erlang_c(cc, TASA_LLEGADA, TASA_SERVICIO)
  cat(sprintf("%-7d %10.3f %10s %10.3f %10s %10.3f\n",
              cc, r$Wq, fmt_teo(teo, "Wq"), r$Lq, fmt_teo(teo, "Lq"), r$rho))
}
