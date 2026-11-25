/// Qué originó el aviso. [otro] cubre tipos que el servidor agregue después.
enum TipoNotificacion {
  pagoAprobado('PAGO_APROBADO'),
  pagoRechazado('PAGO_RECHAZADO'),
  pagoEnRevision('PAGO_EN_REVISION'),
  otro('');

  const TipoNotificacion(this.codigo);
  final String codigo;

  static TipoNotificacion desdeCodigo(String? codigo) => values.firstWhere(
    (t) => t.codigo.isNotEmpty && t.codigo == codigo,
    orElse: () => otro,
  );
}

/// Aviso de la bandeja del usuario. Nace en el servidor cuando cambia el estado
/// de un pago; [upc] enlaza con ese pago.
class Notificacion {
  const Notificacion({
    required this.id,
    required this.tipo,
    required this.titulo,
    required this.mensaje,
    required this.fecha,
    this.upc,
    this.leida = false,
  });

  final int id;
  final TipoNotificacion tipo;
  final String titulo;
  final String mensaje;
  final DateTime fecha;
  final String? upc;
  final bool leida;

  Notificacion marcadaLeida() => Notificacion(
    id: id,
    tipo: tipo,
    titulo: titulo,
    mensaje: mensaje,
    fecha: fecha,
    upc: upc,
    leida: true,
  );

  /// Interpreta el objeto que entrega la API (lista o evento `notificacion`).
  /// Devuelve null si le falta lo indispensable.
  static Notificacion? desdeJson(Object? json) {
    if (json is! Map<String, dynamic>) return null;
    final id = json['id'];
    final fecha = DateTime.tryParse('${json['fecha']}');
    final titulo = json['titulo'];
    final mensaje = json['mensaje'];
    if (id is! num ||
        fecha == null ||
        titulo is! String ||
        mensaje is! String) {
      return null;
    }
    return Notificacion(
      id: id.toInt(),
      tipo: TipoNotificacion.desdeCodigo(json['tipo'] as String?),
      titulo: titulo,
      mensaje: mensaje,
      fecha: fecha,
      upc: json['upc'] as String?,
      leida: json['leida'] == true,
    );
  }
}

/// Una página de la bandeja con el total de avisos sin leer de todo el usuario.
class PaginaNotificaciones {
  const PaginaNotificaciones({
    required this.elementos,
    required this.pagina,
    required this.totalPaginas,
    required this.noLeidas,
  });

  final List<Notificacion> elementos;
  final int pagina;
  final int totalPaginas;
  final int noLeidas;

  bool get hayMas => pagina + 1 < totalPaginas;
}
