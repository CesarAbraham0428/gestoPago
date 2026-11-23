-- Segundo factor con app autenticadora (TOTP, RFC 6238) en lugar del código por correo.
-- El secreto se guarda cifrado (AES-GCM) por la aplicación; "totp_ultimo_paso" evita reutilizar un código.
ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS totp_secreto     varchar(255),
    ADD COLUMN IF NOT EXISTS totp_confirmado  boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS totp_ultimo_paso bigint  NOT NULL DEFAULT 0;

-- El desafío ya no guarda un código: solo enlaza la contraseña validada con la verificación del código TOTP.
-- "verificado" conserva que el código ya fue aceptado cuando hay que confirmar el cierre de otra sesión.
ALTER TABLE desafios_autenticacion
    DROP COLUMN IF EXISTS codigo_hash,
    ADD COLUMN IF NOT EXISTS verificado boolean  NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS paso       bigint   NOT NULL DEFAULT 0;
