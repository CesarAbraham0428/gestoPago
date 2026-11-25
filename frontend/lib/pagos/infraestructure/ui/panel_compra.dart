import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/format/formatters.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../authentication/domain/validadores_credenciales.dart';
import '../../../core/ui/campo_codigo.dart';
import '../../../core/ui/ilustracion.dart';
import '../../../core/ui/tarjeta_debito.dart';
import '../../../core/ui/widgets_estado.dart';
import '../../../productos/domain/entities/producto.dart';
import '../../application/realizar_compra.dart';
import '../../domain/entities/cuenta.dart';
import '../../domain/entities/estado_pago.dart';
import '../../domain/entities/resultado_compra.dart';
import '../../domain/entities/solicitud_compra.dart';
import '../../domain/ports/cuentas_repository.dart';
import '../../domain/ports/pagos_repository.dart';
import '../../domain/validadores_compra.dart';
import 'recibo.dart';

enum _Fase { formulario, enviando, resultado }

/// Flujo de compra de un producto: formulario, confirmación con el código de la app autenticadora, envío y
/// resultado. Cada desenlace (exitosa, rechazada, pendiente, error de
/// comunicación) tiene su propia presentación.
class PanelCompra extends StatefulWidget {
  const PanelCompra({
    super.key,
    required this.producto,
    required this.cuentas,
    required this.pagos,
    required this.realizarCompra,
    required this.alConfirmarCompra,
    required this.alVerHistorial,
  });

  final Producto producto;
  final CuentasRepository cuentas;
  final PagosRepository pagos;
  final RealizarCompra realizarCompra;

  /// Se invoca cuando el servicio confirma una compra, para refrescar historial.
  final VoidCallback alConfirmarCompra;
  final VoidCallback alVerHistorial;

  @override
  State<PanelCompra> createState() => _PanelCompraState();
}

class _PanelCompraState extends State<PanelCompra> {
  final _clave = GlobalKey<FormState>();
  final _telefono = TextEditingController();
  final _referencia = TextEditingController();
  final _monto = TextEditingController();

  List<Cuenta>? _cuentas;
  String? _errorCuentas;
  Cuenta? _cuenta;

  _Fase _fase = _Fase.formulario;
  ResultadoCompra? _resultado;
  SolicitudCompra? _solicitud;
  String? _errorSaldo;

  Producto get _p => widget.producto;
  bool get _pideCelular =>
      _p.tipoCompra == TipoCompra.recarga && !_p.esCanjeable;
  bool get _pideServicio => _p.tipoCompra == TipoCompra.pagoServicio;

  @override
  void initState() {
    super.initState();
    _monto.addListener(() => setState(() => _errorSaldo = null));
    _cargarCuentas();
  }

  @override
  void dispose() {
    _telefono.dispose();
    _referencia.dispose();
    _monto.dispose();
    super.dispose();
  }

  /// Con [silencioso] se refresca sin mostrar el indicador de carga ni perder la
  /// cuenta elegida, y un fallo no reemplaza lo que ya se ve.
  Future<void> _cargarCuentas({bool silencioso = false}) async {
    if (!silencioso) {
      setState(() {
        _cuentas = null;
        _errorCuentas = null;
      });
    }
    try {
      final lista = await widget.cuentas.obtenerActivas();
      if (!mounted) return;
      final elegida = _cuenta?.numeroCuenta;
      setState(() {
        _cuentas = lista;
        _cuenta = lista.isEmpty
            ? null
            : lista.firstWhere(
                (c) => silencioso && c.numeroCuenta == elegida,
                orElse: () => lista.first,
              );
      });
    } on AppException catch (e) {
      if (mounted && !silencioso) setState(() => _errorCuentas = e.message);
    } on Object {
      if (mounted && !silencioso) {
        setState(() => _errorCuentas = 'No pudimos cargar tu cuenta.');
      }
    }
  }

  /// El servidor bloquea los pagos de la cuenta tras varios códigos incorrectos.
  DateTime? get _pinBloqueadoHasta {
    final c = _cuenta;
    return c != null && c.bloqueoRestante() != null
        ? c.pinBloqueadoHasta
        : null;
  }

  double? get _montoIngresado => _p.montoFijo ?? parseMonto(_monto.text);

  double? get _total {
    final monto = _montoIngresado;
    return monto == null ? null : monto + _p.comision;
  }

  Future<void> _revisar() async {
    if (_pinBloqueadoHasta != null) return;
    if (!(_clave.currentState?.validate() ?? false)) return;
    final cuenta = _cuenta;
    final monto = _montoIngresado;
    if (cuenta == null || monto == null) return;
    final total = monto + _p.comision;
    if (cuenta.saldo < total) {
      setState(
        () => _errorSaldo =
            'Tu saldo es ${formatoMoneda(cuenta.saldo)} y esta compra cuesta ${formatoMoneda(total)}.',
      );
      return;
    }
    final codigo = await showDialog<String>(
      context: context,
      barrierDismissible: false,
      builder: (_) => _DialogoConfirmacion(
        producto: _p,
        cuenta: cuenta,
        monto: monto,
        telefono: _pideCelular ? _telefono.text.trim() : null,
        referencia: _pideServicio ? _referencia.text.trim() : null,
      ),
    );
    if (codigo == null || !mounted) return;

    final solicitud = SolicitudCompra(
      producto: _p,
      numeroCuenta: cuenta.numeroCuenta,
      monto: monto,
      codigoOtp: codigo,
      telefono: _pideCelular ? _telefono.text.trim() : null,
      referencia: _pideServicio ? _referencia.text.trim() : null,
    );
    setState(() {
      _fase = _Fase.enviando;
      _solicitud = solicitud;
    });
    final resultado = await widget.realizarCompra.ejecutar(solicitud);
    // Un código incorrecto pudo bloquear la cuenta: se consulta antes de mostrar el resultado.
    if (resultado is CompraRechazada) await _cargarCuentas(silencioso: true);
    if (!mounted) return;
    setState(() {
      _resultado = resultado;
      _fase = _Fase.resultado;
    });
    if (resultado is CompraExitosa) {
      widget.alConfirmarCompra();
      unawaited(_cargarCuentas()); // el saldo cambió
    }
  }

  void _volverAlFormulario({bool limpiar = false}) {
    if (limpiar) {
      _telefono.clear();
      _referencia.clear();
      _monto.clear();
    }
    setState(() {
      _fase = _Fase.formulario;
      _resultado = null;
      _solicitud = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    switch (_fase) {
      case _Fase.enviando:
        return const _Enviando();
      case _Fase.resultado:
        return _VistaResultado(
          resultado: _resultado!,
          solicitud: _solicitud!,
          pinBloqueadoHasta: _pinBloqueadoHasta,
          alDesbloquear: () => setState(() {}),
          pagos: widget.pagos,
          alReintentar: () => _volverAlFormulario(),
          alComprarOtro: () => _volverAlFormulario(limpiar: true),
          alVerHistorial: widget.alVerHistorial,
          alConfirmarCompra: () {
            widget.alConfirmarCompra();
            _cargarCuentas();
          },
        );
      case _Fase.formulario:
        return _formulario(context);
    }
  }

  Widget _formulario(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    if (_errorCuentas != null) {
      return AvisoEstado(
        tono: Tono.rechazo,
        icono: Icons.error_outline,
        titulo: 'No pudimos cargar tu cuenta',
        texto: _errorCuentas!,
        accion: OutlinedButton(
          onPressed: _cargarCuentas,
          child: const Text('Reintentar'),
        ),
      );
    }
    final cuentas = _cuentas;
    if (cuentas == null) {
      return const Padding(padding: EdgeInsets.all(24), child: VistaCargando());
    }
    if (cuentas.isEmpty) {
      return const AvisoEstado(
        tono: Tono.aviso,
        icono: Icons.account_balance_wallet_outlined,
        titulo: 'No tienes una cuenta activa',
        texto:
            'Para comprar necesitas una cuenta activa con saldo. '
            'Comunícate con soporte para activarla.',
      );
    }
    final total = _total;
    return Form(
      key: _clave,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              const Icon(Icons.shopping_bag_outlined, color: AppColors.marca),
              const SizedBox(width: 8),
              Text('Comprar', style: tema.titleLarge),
            ],
          ),
          const SizedBox(height: 16),
          if (cuentas.length == 1)
            _CuentaFija(cuenta: cuentas.first)
          else
            DropdownButtonFormField<Cuenta>(
              initialValue: _cuenta,
              decoration: const InputDecoration(labelText: 'Pagar desde'),
              items: [
                for (final c in cuentas)
                  DropdownMenuItem(
                    value: c,
                    child: Text(
                      '${cuentaEnmascarada(c.numeroCuenta)} · ${formatoMoneda(c.saldo)}',
                    ),
                  ),
              ],
              onChanged: (c) => setState(() {
                _cuenta = c;
                _errorSaldo = null;
              }),
            ),
          const SizedBox(height: 16),
          if (_pideCelular)
            TextFormField(
              controller: _telefono,
              keyboardType: TextInputType.phone,
              autofillHints: const [AutofillHints.telephoneNumber],
              inputFormatters: [
                FilteringTextInputFormatter.digitsOnly,
                LengthLimitingTextInputFormatter(10),
              ],
              decoration: const InputDecoration(
                labelText: 'Número de celular',
                helperText: '10 dígitos',
              ),
              validator: ValidadoresCompra.telefono,
            ),
          if (_pideServicio) ...[
            TextFormField(
              controller: _referencia,
              textCapitalization: TextCapitalization.characters,
              inputFormatters: [
                FilteringTextInputFormatter.allow(RegExp('[A-Za-z0-9]')),
                LengthLimitingTextInputFormatter(100),
              ],
              decoration: InputDecoration(
                labelText: 'Referencia',
                helperText: (_p.tipoReferencia?.isNotEmpty ?? false)
                    ? _p.tipoReferencia
                    : 'La encuentras en tu recibo',
              ),
              validator: ValidadoresCompra.referencia,
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _monto,
              keyboardType: const TextInputType.numberWithOptions(
                decimal: true,
              ),
              inputFormatters: [
                FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]')),
                LengthLimitingTextInputFormatter(16),
              ],
              decoration: const InputDecoration(
                labelText: 'Monto a pagar',
                prefixText: r'$ ',
              ),
              validator: ValidadoresCompra.monto,
            ),
          ],
          const SizedBox(height: 20),
          _TotalLinea(
            etiqueta: 'Total a pagar',
            valor: total == null ? '—' : formatoMoneda(total),
            detalle: _p.comision > 0 && total != null
                ? 'Incluye ${formatoMoneda(_p.comision)} de comisión'
                : null,
          ),
          if (_errorSaldo != null) ...[
            const SizedBox(height: 12),
            AvisoEstado(
              tono: Tono.rechazo,
              icono: Icons.error_outline,
              titulo: 'Saldo insuficiente',
              texto: _errorSaldo!,
            ),
          ],
          const SizedBox(height: 16),
          if (_pinBloqueadoHasta case final hasta?) ...[
            _PinBloqueado(hasta: hasta, alTerminar: () => setState(() {})),
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: null,
              icon: const Icon(Icons.lock_outline),
              label: const Text('Compras bloqueadas'),
            ),
          ] else
            FilledButton.icon(
              onPressed: _revisar,
              icon: const Icon(Icons.shopping_cart_checkout),
              label: const Text('Realizar compra'),
            ),
        ],
      ),
    );
  }
}

class _CuentaFija extends StatelessWidget {
  const _CuentaFija({required this.cuenta});
  final Cuenta cuenta;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          'Pagas desde ${cuentaEnmascarada(cuenta.numeroCuenta)}',
          style: Theme.of(context).textTheme.bodyMedium,
        ),
        const SizedBox(height: 10),
        TarjetaDebito(
          numeroCuenta: cuenta.numeroCuenta,
          saldo: cuenta.saldo,
          activa: cuenta.activa,
        ),
      ],
    );
  }
}

class _TotalLinea extends StatelessWidget {
  const _TotalLinea({
    required this.etiqueta,
    required this.valor,
    this.detalle,
  });
  final String etiqueta;
  final String valor;
  final String? detalle;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Expanded(child: Text(etiqueta, style: tema.bodyMedium)),
            Text(
              valor,
              style: tema.titleLarge?.copyWith(fontFeatures: cifrasTabulares),
            ),
          ],
        ),
        if (detalle != null)
          Align(
            alignment: Alignment.centerRight,
            child: Text(detalle!, style: tema.bodySmall),
          ),
      ],
    );
  }
}

class _Enviando extends StatelessWidget {
  const _Enviando();

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.symmetric(vertical: 48),
      child: VistaCargando(
        mensaje:
            'Procesando tu pago. Puede tardar hasta un minuto; '
            'no cierres esta pantalla.',
      ),
    );
  }
}

/// Confirmación previa a cobrar: resume la operación y pide el código
/// de la app autenticadora.
class _DialogoConfirmacion extends StatefulWidget {
  const _DialogoConfirmacion({
    required this.producto,
    required this.cuenta,
    required this.monto,
    this.telefono,
    this.referencia,
  });

  final Producto producto;
  final Cuenta cuenta;
  final double monto;
  final String? telefono;
  final String? referencia;

  @override
  State<_DialogoConfirmacion> createState() => _DialogoConfirmacionState();
}

class _DialogoConfirmacionState extends State<_DialogoConfirmacion> {
  final _clave = GlobalKey<FormState>();
  final _codigo = TextEditingController();

  @override
  void dispose() {
    _codigo.dispose();
    super.dispose();
  }

  void _confirmar() {
    if (_clave.currentState?.validate() ?? false) {
      Navigator.of(context).pop(_codigo.text);
    }
  }

  @override
  Widget build(BuildContext context) {
    final p = widget.producto;
    final total = widget.monto + p.comision;
    final tema = Theme.of(context).textTheme;
    return AlertDialog(
      title: const Row(
        children: [
          IconoEnRecuadro(
            icono: Icons.verified_user_outlined,
            color: AppColors.marca,
            fondo: AppColors.marcaTenue,
          ),
          SizedBox(width: 12),
          Expanded(child: Text('Confirma tu compra')),
        ],
      ),
      content: SizedBox(
        width: 400,
        child: SingleChildScrollView(
          child: Form(
            key: _clave,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 14,
                    vertical: 6,
                  ),
                  decoration: BoxDecoration(
                    color: AppColors.papel,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: AppColors.lineaSuave),
                  ),
                  child: Column(
                    children: [
                      FilaDato(
                        icono: Icons.shopping_bag_outlined,
                        etiqueta: 'Producto',
                        valor: p.nombre,
                      ),
                      FilaDato(
                        icono: Icons.storefront_outlined,
                        etiqueta: 'Servicio',
                        valor: p.servicio,
                      ),
                      if (widget.telefono != null)
                        FilaDato(
                          icono: Icons.smartphone_outlined,
                          etiqueta: 'Celular',
                          valor: formatoTelefono(widget.telefono!),
                        ),
                      if (widget.referencia != null)
                        FilaDato(
                          icono: Icons.tag,
                          etiqueta: 'Referencia',
                          valor: widget.referencia!,
                        ),
                      FilaDato(
                        icono: Icons.payments_outlined,
                        etiqueta: 'Importe',
                        valor: formatoMoneda(widget.monto),
                      ),
                      if (p.comision > 0)
                        FilaDato(
                          icono: Icons.percent,
                          etiqueta: 'Comisión',
                          valor: formatoMoneda(p.comision),
                        ),
                      FilaDato(
                        icono: Icons.account_balance_wallet_outlined,
                        etiqueta: 'Cuenta',
                        valor: cuentaEnmascarada(widget.cuenta.numeroCuenta),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 10),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 14,
                    vertical: 4,
                  ),
                  decoration: BoxDecoration(
                    color: AppColors.marcaTenue,
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: FilaDato(
                    icono: Icons.receipt_long_outlined,
                    etiqueta: 'Total',
                    valor: formatoMoneda(total),
                    destacado: true,
                  ),
                ),
                const SizedBox(height: 16),
                Text('Código de tu app autenticadora', style: tema.labelLarge),
                const SizedBox(height: 8),
                CampoCodigo(
                  controller: _codigo,
                  autofocus: true,
                  validator: ValidadoresCredenciales.codigo,
                  alEnviar: _confirmar,
                ),
                const SizedBox(height: 8),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(
                      Icons.info_outline,
                      size: 16,
                      color: AppColors.tintaSuave,
                    ),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        'Al pagar, ${formatoMoneda(total)} se descuentan de tu saldo.',
                        style: tema.bodySmall,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancelar'),
        ),
        FilledButton.icon(
          onPressed: _confirmar,
          icon: const Icon(Icons.lock_outline, size: 18),
          label: Text('Pagar ${formatoMoneda(total)}'),
        ),
      ],
    );
  }
}

/// Compra rechazada: qué pasó y, sobre todo, que no se cobró nada.
class _Rechazo extends StatelessWidget {
  const _Rechazo({required this.mensaje, required this.alReintentar});
  final String mensaje;
  final VoidCallback alReintentar;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return Semantics(
      liveRegion: true,
      child: Container(
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 18),
        decoration: BoxDecoration(
          color: AppColors.rechazoFondo.withValues(alpha: 0.55),
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: AppColors.rechazo.withValues(alpha: 0.2)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Center(
              child: Ilustracion(
                icono: Icons.close_rounded,
                color: AppColors.rechazo,
                fondo: AppColors.rechazoFondo,
                tamano: 104,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Compra rechazada',
              textAlign: TextAlign.center,
              style: tema.titleLarge?.copyWith(color: AppColors.rechazo),
            ),
            const SizedBox(height: 6),
            Text(mensaje, textAlign: TextAlign.center, style: tema.bodyMedium),
            const SizedBox(height: 14),
            Center(
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 7,
                ),
                decoration: BoxDecoration(
                  color: AppColors.exitoFondo,
                  borderRadius: BorderRadius.circular(999),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(
                      Icons.verified_user_outlined,
                      size: 16,
                      color: AppColors.exito,
                    ),
                    const SizedBox(width: 6),
                    Flexible(
                      child: Text(
                        'No se hizo ningún cargo a tu cuenta.',
                        style: tema.bodySmall?.copyWith(
                          color: AppColors.exito,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: alReintentar,
              icon: const Icon(Icons.refresh, size: 18),
              label: const Text('Corregir y reintentar'),
            ),
          ],
        ),
      ),
    );
  }
}

/// Pagos bloqueados por códigos incorrectos: explica el bloqueo y cuenta hacia atrás
/// hasta que se pueda volver a pagar; entonces avisa con [alTerminar].
class _PinBloqueado extends StatefulWidget {
  const _PinBloqueado({required this.hasta, required this.alTerminar});
  final DateTime hasta;
  final VoidCallback alTerminar;

  @override
  State<_PinBloqueado> createState() => _PinBloqueadoState();
}

class _PinBloqueadoState extends State<_PinBloqueado> {
  Timer? _reloj;
  late Duration _resta;

  @override
  void initState() {
    super.initState();
    _resta = _calcular();
    _reloj = Timer.periodic(const Duration(seconds: 1), (_) {
      final resta = _calcular();
      if (resta <= Duration.zero) {
        _reloj?.cancel();
        widget.alTerminar();
      } else {
        setState(() => _resta = resta);
      }
    });
  }

  Duration _calcular() => widget.hasta.difference(DateTime.now());

  @override
  void dispose() {
    _reloj?.cancel();
    super.dispose();
  }

  String get _cuenta {
    final s = _resta.inSeconds < 0 ? 0 : _resta.inSeconds;
    return '${s ~/ 60}:${(s % 60).toString().padLeft(2, '0')}';
  }

  String get _hora {
    final h = widget.hasta.toLocal();
    return '${h.hour.toString().padLeft(2, '0')}:${h.minute.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return Semantics(
      liveRegion: true,
      child: Container(
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 18),
        decoration: BoxDecoration(
          color: AppColors.coralTenue.withValues(alpha: 0.6),
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: AppColors.coral.withValues(alpha: 0.3)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Center(
              child: Ilustracion(
                icono: Icons.lock_clock_outlined,
                color: AppColors.coral,
                fondo: AppColors.coralTenue,
                tamano: 104,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Pagos bloqueados temporalmente',
              textAlign: TextAlign.center,
              style: tema.titleLarge?.copyWith(color: AppColors.coral),
            ),
            const SizedBox(height: 6),
            Text(
              'Por tu seguridad bloqueamos las compras de esta cuenta tras '
              'varios intentos con un código incorrecto. No se hizo ningún cargo.',
              textAlign: TextAlign.center,
              style: tema.bodyMedium,
            ),
            const SizedBox(height: 14),
            Center(
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 8,
                ),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(999),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(
                      Icons.timer_outlined,
                      size: 20,
                      color: AppColors.coral,
                    ),
                    const SizedBox(width: 8),
                    Text(
                      _cuenta,
                      style: tema.titleLarge?.copyWith(
                        fontFeatures: cifrasTabulares,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Podrás volver a comprar a las $_hora.',
              textAlign: TextAlign.center,
              style: tema.bodySmall,
            ),
          ],
        ),
      ),
    );
  }
}

/// Presentación de cada desenlace de la compra.
class _VistaResultado extends StatefulWidget {
  const _VistaResultado({
    required this.resultado,
    required this.solicitud,
    required this.pinBloqueadoHasta,
    required this.alDesbloquear,
    required this.pagos,
    required this.alReintentar,
    required this.alComprarOtro,
    required this.alVerHistorial,
    required this.alConfirmarCompra,
  });

  final ResultadoCompra resultado;
  final SolicitudCompra solicitud;

  /// Hasta cuándo están bloqueados los pagos de la cuenta (si lo está).
  final DateTime? pinBloqueadoHasta;
  final VoidCallback alDesbloquear;
  final PagosRepository pagos;
  final VoidCallback alReintentar;
  final VoidCallback alComprarOtro;
  final VoidCallback alVerHistorial;
  final VoidCallback alConfirmarCompra;

  @override
  State<_VistaResultado> createState() => _VistaResultadoState();
}

class _VistaResultadoState extends State<_VistaResultado> {
  bool _consultando = false;
  EstadoPago? _estado;
  String? _errorConsulta;

  Future<void> _consultar(String upc) async {
    setState(() {
      _consultando = true;
      _errorConsulta = null;
    });
    try {
      final estado = await widget.pagos.consultarEstado(upc);
      if (!mounted) return;
      setState(() => _estado = estado);
      if (estado.aprobado) widget.alConfirmarCompra();
    } on AppException catch (e) {
      if (mounted) setState(() => _errorConsulta = e.message);
    } on Object {
      if (mounted)
        setState(() => _errorConsulta = 'No pudimos consultar el estado.');
    }
    if (mounted) setState(() => _consultando = false);
  }

  @override
  Widget build(BuildContext context) {
    return switch (widget.resultado) {
      CompraExitosa(:final comprobante) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Recibo(
            datos: DatosRecibo.deComprobante(comprobante, widget.solicitud),
          ),
          const SizedBox(height: 20),
          FilledButton(
            onPressed: widget.alVerHistorial,
            child: const Text('Ver mis compras'),
          ),
          const SizedBox(height: 8),
          OutlinedButton(
            onPressed: widget.alComprarOtro,
            child: const Text('Comprar otro'),
          ),
        ],
      ),
      CompraRechazada(:final mensaje) =>
        widget.pinBloqueadoHasta == null
            ? _Rechazo(mensaje: mensaje, alReintentar: widget.alReintentar)
            : Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _PinBloqueado(
                    hasta: widget.pinBloqueadoHasta!,
                    alTerminar: widget.alDesbloquear,
                  ),
                  const SizedBox(height: 12),
                  FilledButton(
                    onPressed: widget.alReintentar,
                    child: const Text('Entendido'),
                  ),
                ],
              ),
      CompraPendiente(:final mensaje, :final referencia) => _pendiente(
        mensaje,
        referencia,
      ),
      ErrorDeComunicacion(:final mensaje, :final resultadoIncierto) =>
        AvisoEstado(
          tono: resultadoIncierto ? Tono.pendiente : Tono.aviso,
          icono: Icons.wifi_off_outlined,
          titulo: resultadoIncierto
              ? 'No pudimos confirmar el resultado'
              : 'No se pudo completar la compra',
          texto: mensaje,
          accion: resultadoIncierto
              ? FilledButton(
                  onPressed: widget.alVerHistorial,
                  child: const Text('Revisar mis compras'),
                )
              : FilledButton(
                  onPressed: widget.alReintentar,
                  child: const Text('Intentar de nuevo'),
                ),
        ),
    };
  }

  Widget _pendiente(String mensaje, String? referencia) {
    final estado = _estado;
    final tema = Theme.of(context).textTheme;
    if (estado != null && estado.aprobado) {
      return AvisoEstado(
        tono: Tono.exito,
        icono: Icons.check_circle_outline,
        titulo: 'Compra confirmada',
        texto: 'El servicio confirmó tu pago. Ya aparece en tus compras.',
        accion: FilledButton(
          onPressed: widget.alVerHistorial,
          child: const Text('Ver mis compras'),
        ),
      );
    }
    if (estado != null && estado.rechazado) {
      return AvisoEstado(
        tono: Tono.rechazo,
        icono: Icons.cancel_outlined,
        titulo: 'Compra rechazada',
        texto: 'El pago no se realizó y tu saldo fue devuelto.',
        accion: FilledButton(
          onPressed: widget.alComprarOtro,
          child: const Text('Volver al catálogo'),
        ),
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        AvisoEstado(
          tono: Tono.pendiente,
          icono: Icons.schedule,
          titulo: 'Compra pendiente',
          texto: mensaje,
        ),
        if (referencia != null) ...[
          const SizedBox(height: 16),
          FilaDato(etiqueta: 'Referencia', valor: referencia),
          const SizedBox(height: 8),
          OutlinedButton(
            onPressed: _consultando ? null : () => _consultar(referencia),
            child: _consultando
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Consultar estado'),
          ),
          if (estado != null && estado.enProceso)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(
                'Sigue en proceso. Vuelve a consultar en unos minutos.',
                style: tema.bodySmall,
              ),
            ),
          if (_errorConsulta != null)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(
                _errorConsulta!,
                style: tema.bodySmall?.copyWith(color: AppColors.rechazo),
              ),
            ),
        ],
        const SizedBox(height: 8),
        TextButton(
          onPressed: widget.alVerHistorial,
          child: const Text('Ir a mis compras'),
        ),
      ],
    );
  }
}
