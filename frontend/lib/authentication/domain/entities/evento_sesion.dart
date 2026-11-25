/// Un evento del canal de la sesión (Server-Sent Events): su nombre y sus datos.
/// Hay un solo canal por sesión, así que el cierre de sesión y las notificaciones
/// comparten la conexión.
typedef EventoSesion = ({String nombre, String datos});
