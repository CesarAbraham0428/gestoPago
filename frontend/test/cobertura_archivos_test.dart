// Importa todos los archivos de lib/ (salvo los main de arranque) para que
// `flutter test --coverage` los incluya aunque ningún otro test los importe.
// ignore_for_file: unused_import

import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app.dart';
import 'package:frontend/app/dependencias.dart';
import 'package:frontend/app/mobile/mobile_shell.dart';
import 'package:frontend/app/router.dart';
import 'package:frontend/app/shell_comun.dart';
import 'package:frontend/app/web/web_shell.dart';
import 'package:frontend/authentication/application/sesion_controller.dart';
import 'package:frontend/authentication/domain/entities/desafio_verificacion.dart';
import 'package:frontend/authentication/domain/entities/sesion.dart';
import 'package:frontend/authentication/domain/ports/almacen_sesion.dart';
import 'package:frontend/authentication/domain/ports/autenticacion_gateway.dart';
import 'package:frontend/authentication/domain/ports/registro_gateway.dart';
import 'package:frontend/authentication/domain/validadores_credenciales.dart';
import 'package:frontend/authentication/domain/validadores_registro.dart';
import 'package:frontend/authentication/infraestructure/adapters/api_registro_gateway.dart';
import 'package:frontend/authentication/infraestructure/ui/registro_page.dart';
import 'package:frontend/core/ui/fondo_acceso.dart';
import 'package:frontend/authentication/infraestructure/adapters/almacen_sesion_seguro.dart';
import 'package:frontend/authentication/infraestructure/adapters/api_autenticacion_gateway.dart';
import 'package:frontend/authentication/infraestructure/ui/guardia_inactividad.dart';
import 'package:frontend/authentication/infraestructure/ui/login_page.dart';
import 'package:frontend/core/config/app_config.dart';
import 'package:frontend/core/error/app_exception.dart';
import 'package:frontend/core/format/formatters.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/theme/app_colors.dart';
import 'package:frontend/core/theme/app_theme.dart';
import 'package:frontend/core/ui/breakpoints.dart';
import 'package:frontend/core/ui/ilustracion.dart';
import 'package:frontend/core/ui/monograma.dart';
import 'package:frontend/core/ui/widgets_estado.dart';
import 'package:frontend/pagos/application/historial_controller.dart';
import 'package:frontend/pagos/application/realizar_compra.dart';
import 'package:frontend/pagos/domain/entities/compra_historial.dart';
import 'package:frontend/pagos/domain/entities/comprobante.dart';
import 'package:frontend/pagos/domain/entities/cuenta.dart';
import 'package:frontend/pagos/domain/entities/estado_pago.dart';
import 'package:frontend/pagos/domain/entities/resultado_compra.dart';
import 'package:frontend/pagos/domain/entities/solicitud_compra.dart';
import 'package:frontend/pagos/domain/ports/cuentas_repository.dart';
import 'package:frontend/pagos/domain/ports/pagos_repository.dart';
import 'package:frontend/pagos/domain/validadores_compra.dart';
import 'package:frontend/pagos/infraestructure/adapters/api_cuentas_repository.dart';
import 'package:frontend/pagos/infraestructure/adapters/api_pagos_repository.dart';
import 'package:frontend/pagos/infraestructure/ui/historial_page.dart';
import 'package:frontend/pagos/infraestructure/ui/panel_compra.dart';
import 'package:frontend/pagos/infraestructure/ui/recibo.dart';
import 'package:frontend/perfil/application/perfil_controller.dart';
import 'package:frontend/perfil/domain/entities/perfil.dart';
import 'package:frontend/perfil/domain/ports/perfil_repository.dart';
import 'package:frontend/perfil/infraestructure/adapters/api_perfil_repository.dart';
import 'package:frontend/perfil/infraestructure/ui/formulario_perfil.dart';
import 'package:frontend/perfil/infraestructure/ui/perfil_page.dart';
import 'package:frontend/core/ui/logo_app.dart';
import 'package:frontend/core/ui/campo_codigo.dart';
import 'package:frontend/core/error/mensajes_usuario.dart';
import 'package:frontend/productos/application/catalogo_controller.dart';
import 'package:frontend/productos/domain/entities/producto.dart';
import 'package:frontend/productos/domain/ports/catalogo_repository.dart';
import 'package:frontend/productos/infraestructure/adapters/api_catalogo_repository.dart';
import 'package:frontend/productos/infraestructure/ui/catalogo_page.dart';
import 'package:frontend/productos/infraestructure/ui/detalle_producto.dart';

void main() {
  test('todos los archivos de lib/ se incluyen en la cobertura', () {});
}
