-- Sesión única: a lo sumo una fila por usuario. La clave primaria garantiza que no existan dos
-- sesiones activas y permite reemplazarla de forma atómica (INSERT ... ON CONFLICT).
CREATE TABLE IF NOT EXISTS sesiones_usuario (
    usuario_id integer PRIMARY KEY REFERENCES usuarios (id),
    jti        varchar(36) NOT NULL,
    creada_en  timestamp with time zone NOT NULL,
    expira_en  timestamp with time zone NOT NULL
);

-- Segundo factor: código de un solo uso enviado por correo. Solo se guarda su hash y a lo sumo un
-- desafío vigente por usuario.
CREATE TABLE IF NOT EXISTS desafios_autenticacion (
    id          varchar(64) PRIMARY KEY,
    usuario_id  integer NOT NULL UNIQUE REFERENCES usuarios (id),
    codigo_hash varchar(64) NOT NULL,
    intentos    smallint NOT NULL DEFAULT 0,
    creado_en   timestamp with time zone NOT NULL,
    expira_en   timestamp with time zone NOT NULL,
    CONSTRAINT ck_desafios_intentos CHECK (intentos >= 0)
);

CREATE INDEX IF NOT EXISTS ix_desafios_expira_en ON desafios_autenticacion (expira_en);
