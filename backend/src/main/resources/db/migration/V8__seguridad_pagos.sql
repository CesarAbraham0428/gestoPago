-- Bloqueo temporal del PIN de autorización tras varios intentos fallidos.
ALTER TABLE cuentas ADD COLUMN IF NOT EXISTS intentos_pin_fallidos smallint NOT NULL DEFAULT 0;
ALTER TABLE cuentas ADD COLUMN IF NOT EXISTS pin_bloqueado_hasta timestamp with time zone;

-- Comisión que se cobra al cliente en pagos de servicio (precio del catálogo con tipoFront 2).
-- Se descuenta monto + comision_cliente y se devuelve lo mismo si el pago no se aplica.
ALTER TABLE pagos ADD COLUMN IF NOT EXISTS comision_cliente numeric(18,2) NOT NULL DEFAULT 0;
ALTER TABLE pagos ADD CONSTRAINT ck_pagos_comision_cliente CHECK (comision_cliente >= 0);

-- Bloqueo optimista: dos conciliaciones simultáneas del mismo pago no pueden devolver el saldo dos veces.
ALTER TABLE pagos ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

-- El token de GestoPago ahora se guarda cifrado; los tokens en claro se descartan y se renuevan solos.
DELETE FROM gestopago_tokens;
