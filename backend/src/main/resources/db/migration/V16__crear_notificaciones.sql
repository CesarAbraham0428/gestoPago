-- Bandeja de notificaciones del usuario. Cada cambio de estado de un pago deja una fila en la misma
-- transacción, para que el titular se entere aunque no estuviera conectado (o no haya sido quien pagó).
CREATE TABLE IF NOT EXISTS notificaciones (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id integer NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    tipo varchar(30) NOT NULL,
    titulo varchar(100) NOT NULL,
    mensaje varchar(250) NOT NULL,
    upc varchar(15),
    leida boolean NOT NULL DEFAULT false,
    fecha_creacion timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_notificaciones_tipo CHECK (tipo IN ('PAGO_APROBADO', 'PAGO_RECHAZADO', 'PAGO_EN_REVISION'))
);

CREATE INDEX IF NOT EXISTS idx_notificaciones_usuario ON notificaciones (usuario_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_notificaciones_no_leidas ON notificaciones (usuario_id) WHERE NOT leida;
