package com.proyecto.servicios.models.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenericResponse<T> {
    private Integer codigo;
    private String mensaje;
    private T datos;

    public static <T> GenericResponse<T> exito(String mensaje, T datos) {
        GenericResponse<T> response = new RespuestaExitosa<>();
        response.setCodigo(0);
        response.setMensaje(mensaje);
        response.setDatos(datos);
        return response;
    }

    public static GenericResponse<Void> error(String mensaje) {
        GenericResponse<Void> response = new GenericResponse<>();
        response.setCodigo(1);
        response.setMensaje(mensaje);
        return response;
    }

    /** Una operación exitosa sin contenido conserva datos: null en su contrato. */
    private static final class RespuestaExitosa<T> extends GenericResponse<T> {
        @Override
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public T getDatos() {
            return super.getDatos();
        }
    }
}
