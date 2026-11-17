-- PIN de autorización de pagos (BCrypt, 60 caracteres). Nulo hasta que el cliente lo configure.
ALTER TABLE cuentas ADD COLUMN IF NOT EXISTS pin_hash varchar(60);

-- Bitácora de pagos de servicios enviados a GestoPago.
-- El saldo se retiene (descuenta) al registrar el pago en PENDIENTE y se libera si GestoPago lo rechaza.
CREATE TABLE IF NOT EXISTS pagos (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cuenta_id integer NOT NULL REFERENCES cuentas(id) ON DELETE RESTRICT,
    usuario_id integer NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    id_producto integer NOT NULL,
    id_servicio integer NOT NULL,
    tipo_front integer,
    telefono varchar(10),
    referencia varchar(100),
    monto numeric(18,2) NOT NULL,
    upc varchar(15) NOT NULL,
    estado varchar(20) NOT NULL,
    codigo_respuesta varchar(4),
    id_tx bigint,
    num_autorizacion varchar(60),
    comision numeric(18,2),
    mensaje varchar(250),
    fecha_creacion timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    fecha_actualizacion timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_pagos_upc UNIQUE (upc),
    CONSTRAINT ck_pagos_monto CHECK (monto > 0 AND monto <> 'NaN'::numeric),
    CONSTRAINT ck_pagos_estado CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'EN_REVISION')),
    CONSTRAINT ck_pagos_destino CHECK (telefono IS NOT NULL OR referencia IS NOT NULL),
    CONSTRAINT ck_pagos_telefono CHECK (telefono IS NULL OR telefono ~ '^[0-9]{10}$')
);

CREATE INDEX IF NOT EXISTS idx_pagos_cuenta ON pagos (cuenta_id);
CREATE INDEX IF NOT EXISTS idx_pagos_estado ON pagos (estado);

CREATE TRIGGER trg_auditoria_pagos BEFORE UPDATE ON pagos
    FOR EACH ROW EXECUTE FUNCTION actualizar_auditoria();
CREATE TRIGGER trg_no_eliminar_pagos BEFORE DELETE ON pagos
    FOR EACH ROW EXECUTE FUNCTION impedir_eliminacion_fisica();
