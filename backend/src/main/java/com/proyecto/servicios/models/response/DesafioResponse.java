package com.proyecto.servicios.models.response;

/**
 * Respuesta del primer paso: la contraseña es correcta y se debe verificar el código de la app autenticadora con
 * {@code desafioId}. {@code enrolamiento} solo viene mientras el usuario no ha enlazado su app.
 */
public record DesafioResponse(String desafioId, long expiraEnMs, Enrolamiento enrolamiento) {

    /** Datos para enlazar la app: el secreto (clave manual) y la URI {@code otpauth://} del código QR. */
    public record Enrolamiento(String secreto, String otpauthUri) {
        @Override
        public String toString() {
            return "Enrolamiento[***]"; // el secreto no debe llegar a los logs
        }
    }
}
