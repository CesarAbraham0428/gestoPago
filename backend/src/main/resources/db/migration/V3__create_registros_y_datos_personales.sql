-- Credenciales y perfil se guardan por separado. El hash nunca contiene la contraseña original.
CREATE TABLE IF NOT EXISTS personas (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(120),
    apellido_paterno VARCHAR(120),
    apellido_materno VARCHAR(120),
    correo VARCHAR(254),
    telefono VARCHAR(30)
);

ALTER TABLE personas ADD COLUMN IF NOT EXISTS correo VARCHAR(254);
ALTER TABLE personas ADD COLUMN IF NOT EXISTS telefono VARCHAR(30);

CREATE TABLE IF NOT EXISTS registro (
    id SERIAL PRIMARY KEY,
    usuario VARCHAR(80) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    persona_id INTEGER NOT NULL UNIQUE REFERENCES personas(id) ON DELETE CASCADE,
    CONSTRAINT uk_registro_usuario UNIQUE (usuario)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_registro_usuario_ci ON registro (LOWER(usuario));
