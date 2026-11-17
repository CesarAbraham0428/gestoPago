import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/format/formatters.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
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

/// Flujo de compra de un producto: formulario, confirmación con PIN, envío y
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
  bool get _pideCelular => _p.tipoCompra == TipoCompra.recarga && !_p.esCanjeable;
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

  Future<void> _cargarCuentas() async {
    setState(() {
      _cuentas = null;
      _errorCuentas = null;
    });
    try {
      final lista = await widget.cuentas.obtenerActivas();
      if (!mounted) return;
      setState(() {
        _cuentas = lista;
        _cuenta = lista.isEmpty ? null : lista.first;
      });
    } on AppException catch (e) {
      if (mounted) setState(() => _errorCuentas = e.message);
    } on Object {
      if (mounted) setState(() => _errorCuentas = 'No pudimos cargar tu cuenta.');
    }
  }

  double? get _montoIngresado =>
      _p.montoFijo ?? parseMonto(_monto.text);

  double? get _total {
    final monto = _montoIngresado;
    return monto == null ? null : monto + _p.comision;
  }

  Future<void> _revisar() async {
    if (!(_clave.currentState?.validate() ?? false)) return;
    final cuenta = _cuenta;
    final monto = _montoIngresado;
    if (cuenta == null || monto == null) return;
    final total = monto + _p.comision;
    if (cuenta.saldo < total) {
      setState(() => _errorSaldo =
          'Tu saldo es ${formatoMoneda(cuenta.saldo)} y esta compra cuesta ${formatoMoneda(total)}.');
      return;
    }
    final pin = await showDialog<String>(
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
    if (pin == null || !mounted) return;

    final solicitud = SolicitudCompra(
      producto: _p,
      numeroCuenta: cuenta.numeroCuenta,
      monto: monto,
      pin: pin,
      telefono: _pideCelular ? _telefono.text.trim() : null,
      referencia: _pideServicio ? _referencia.text.trim() : null,
    );
    setState(() {
      _fase = _Fase.enviando;
      _solicitud = solicitud;
    });
    final resultado = await widget.realizarCompra.ejecutar(solicitud);
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
        accion: OutlinedButton(onPressed: _cargarCuentas, child: const Text('Reintentar')),
      );
    }
    final cuentas = _cuentas;
    if (cuentas == null) {
      return const Padding(
        padding: EdgeInsets.all(24),
        child: VistaCargando(),
      );
    }
    if (cuentas.isEmpty) {
      return const AvisoEstado(
        tono: Tono.aviso,
        icono: Icons.account_balance_wallet_outlined,
        titulo: 'No tienes una cuenta activa',
        texto: 'Para comprar necesitas una cuenta activa con saldo. '
            'Comunícate con soporte para activarla.',
      );
    }
    final total = _total;
    return Form(
      key: _clave,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('Comprar', style: tema.titleLarge),
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
                    child: Text('${cuentaEnmascarada(c.numeroCuenta)} · ${formatoMoneda(c.saldo)}'),
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
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
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
          FilledButton(onPressed: _revisar, child: const Text('Revisar compra')),
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
    final tema = Theme.of(context).textTheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: AppColors.superficie,
        border: Border.all(color: AppColors.linea),
        borderRadius: BorderRadius.circular(AppTheme.radio),
      ),
      child: Row(
        children: [
          const Icon(Icons.account_balance_wallet_outlined, size: 20, color: AppColors.tintaSuave),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              'Pagas desde ${cuentaEnmascarada(cuenta.numeroCuenta)}',
              style: tema.bodyMedium,
            ),
          ),
          Text(
            formatoMoneda(cuenta.saldo),
            style: tema.titleMedium?.copyWith(fontFeatures: cifrasTabulares),
          ),
        ],
      ),
    );
  }
}

class _TotalLinea extends StatelessWidget {
  const _TotalLinea({required this.etiqueta, required this.valor, this.detalle});
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
        mensaje: 'Procesando tu pago. Puede tardar hasta un minuto; '
            'no cierres esta pantalla.',
      ),
    );
  }
}

/// Confirmación previa a cobrar: resume la operación y pide el PIN.
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
  final _pin = TextEditingController();
  bool _ocultar = true;

  @override
  void dispose() {
    _pin.dispose();
    super.dispose();
  }

  void _confirmar() {
    if (_clave.currentState?.validate() ?? false) {
      Navigator.of(context).pop(_pin.text);
    }
  }

  @override
  Widget build(BuildContext context) {
    final p = widget.producto;
    final total = widget.monto + p.comision;
    final tema = Theme.of(context).textTheme;
    return AlertDialog(
      title: const Text('Confirma tu compra'),
      content: SizedBox(
        width: 400,
        child: SingleChildScrollView(
          child: Form(
            key: _clave,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                FilaDato(etiqueta: 'Producto', valor: p.nombre),
                FilaDato(etiqueta: 'Servicio', valor: p.servicio),
                if (widget.telefono != null)
                  FilaDato(etiqueta: 'Celular', valor: formatoTelefono(widget.telefono!)),
                if (widget.referencia != null)
                  FilaDato(etiqueta: 'Referencia', valor: widget.referencia!),
                FilaDato(etiqueta: 'Importe', valor: formatoMoneda(widget.monto)),
                if (p.comision > 0)
                  FilaDato(etiqueta: 'Comisión', valor: formatoMoneda(p.comision)),
                FilaDato(etiqueta: 'Total', valor: formatoMoneda(total), destacado: true),
                FilaDato(
                  etiqueta: 'Cuenta',
                  valor: cuentaEnmascarada(widget.cuenta.numeroCuenta),
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _pin,
                  autofocus: true,
                  obscureText: _ocultar,
                  keyboardType: TextInputType.number,
                  textInputAction: TextInputAction.done,
                  enableSuggestions: false,
                  autocorrect: false,
                  inputFormatters: [
                    FilteringTextInputFormatter.digitsOnly,
                    LengthLimitingTextInputFormatter(6),
                  ],
                  onFieldSubmitted: (_) => _confirmar(),
                  decoration: InputDecoration(
                    labelText: 'PIN de autorización',
                    helperText: 'De 4 a 6 dígitos',
                    suffixIcon: IconButton(
                      tooltip: _ocultar ? 'Mostrar PIN' : 'Ocultar PIN',
                      icon: Icon(_ocultar ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                      onPressed: () => setState(() => _ocultar = !_ocultar),
                    ),
                  ),
                  validator: ValidadoresCompra.pin,
                ),
                const SizedBox(height: 8),
                Text(
                  'Al pagar, ${formatoMoneda(total)} se descuentan de tu saldo.',
                  style: tema.bodySmall,
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
        FilledButton(
          onPressed: _confirmar,
          child: Text('Pagar ${formatoMoneda(total)}'),
        ),
      ],
    );
  }
}

/// Presentación de cada desenlace de la compra.
class _VistaResultado extends StatefulWidget {
  const _VistaResultado({
    required this.resultado,
    required this.solicitud,
    required this.pagos,
    required this.alReintentar,
    required this.alComprarOtro,
    required this.alVerHistorial,
    required this.alConfirmarCompra,
  });

  final ResultadoCompra resultado;
  final SolicitudCompra solicitud;
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
      if (mounted) setState(() => _errorConsulta = 'No pudimos consultar el estado.');
    }
    if (mounted) setState(() => _consultando = false);
  }

  @override
  Widget build(BuildContext context) {
    return switch (widget.resultado) {
      CompraExitosa(:final comprobante) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Recibo(datos: DatosRecibo.deComprobante(comprobante, widget.solicitud)),
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
      CompraRechazada(:final mensaje) => AvisoEstado(
        tono: Tono.rechazo,
        icono: Icons.cancel_outlined,
        titulo: 'Compra rechazada',
        texto: '$mensaje\nNo se hizo ningún cargo a tu cuenta.',
        accion: FilledButton(
          onPressed: widget.alReintentar,
          child: const Text('Corregir y reintentar'),
        ),
      ),
      CompraPendiente(:final mensaje, :final referencia) => _pendiente(mensaje, referencia),
      ErrorDeComunicacion(:final mensaje, :final resultadoIncierto) => AvisoEstado(
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
