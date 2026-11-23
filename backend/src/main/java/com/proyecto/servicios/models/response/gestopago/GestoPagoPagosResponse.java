package com.proyecto.servicios.models.response.gestopago;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Respuesta XML de sendTx.do y confirmTx.do. ID_TX, NUM_AUTORIZACION, SALDO... van en la raíz y
 * CODIGO, TEXTO, REFERENCIA, PIN y legend dentro de MENSAJE.
 * SALDO y COMISION pueden venir en notación científica, por eso se leen como BigDecimal.
 *
 * sendTx: 01 éxito; 02 saldo insuficiente del distribuidor; 06 duplicado; 82 tiempo agotado (confirmar).
 * confirmTx: 06 la transacción sí se aplicó; 70 no se aplicó.
 */
@Getter
@Setter
@NoArgsConstructor
public class GestoPagoPagosResponse {

    public static final String CODIGO_EXITOSO = "01";
    // Saldo insuficiente del distribuidor en GestoPago, no el de la cuenta del cliente.
    public static final String CODIGO_SALDO_INSUFICIENTE = "02";
    public static final String CODIGO_DUPLICADO = "06";
    public static final String CODIGO_TIEMPO_AGOTADO = "82";
    public static final String CODIGO_CONFIRMADO_APLICADO = "06";
    public static final String CODIGO_CONFIRMADO_NO_APLICADO = "70";

    private String codigo;
    private Long idTx;
    private String numAutorizacion;
    private BigDecimal saldo;
    private String saldoF;
    private BigDecimal comision;
    private String comisionF;
    /** Formato d/m/Y H:i:s tal como lo envía GestoPago. */
    private String fecha;
    private BigDecimal monto;
    private String referencia;
    private String pin;
    private String legend;
    /** Texto de error que GestoPago acompaña a los códigos distintos de 01, si lo envía. */
    private String mensaje;
    /** Cuerpo recibido tal cual, para el log de evidencia que exige GestoPago. */
    private String respuestaOriginal;

    public boolean esExitoso() {
        return esCodigo(CODIGO_EXITOSO);
    }

    public boolean esSaldoInsuficiente() {
        return esCodigo(CODIGO_SALDO_INSUFICIENTE);
    }

    // GestoPago documenta CODIGO como entero: "01" y "1" son el mismo código.
    public boolean esCodigo(String esperado) {
        if (codigo == null) {
            return false;
        }
        try {
            return Integer.parseInt(codigo.trim()) == Integer.parseInt(esperado);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
