-- Evoluciona V4 y el esquema manual sin modificar migraciones ya aplicadas.
ALTER TABLE clientes ALTER COLUMN nacionalidad DROP DEFAULT;
ALTER TABLE clientes ALTER COLUMN nacionalidad TYPE varchar(100) USING nacionalidad::text;
ALTER TABLE clientes ALTER COLUMN nacionalidad SET DEFAULT 'Mexicana';
ALTER TABLE clientes ADD CONSTRAINT ck_clientes_nacionalidad CHECK (length(btrim(nacionalidad)) > 0);

ALTER TABLE clientes ADD CONSTRAINT ck_clientes_nombres_letras CHECK (
    primer_nombre ~ '^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ ]+$'
    AND apellido_paterno ~ '^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ ]+$'
    AND apellido_materno ~ '^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ ]+$'
    AND (segundo_nombre IS NULL OR segundo_nombre ~ '^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ ]+$')
);

-- BCrypt siempre almacena una representación de 60 caracteres.
ALTER TABLE usuarios ALTER COLUMN password_hash TYPE varchar(60);

-- Alinea las fechas del trigger con la zona empleada por la capa de servicio.
CREATE OR REPLACE FUNCTION validar_cliente() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    hoy date := (CURRENT_TIMESTAMP AT TIME ZONE 'America/Mexico_City')::date;
BEGIN
    NEW.primer_nombre := btrim(NEW.primer_nombre);
    NEW.segundo_nombre := btrim(NEW.segundo_nombre);
    NEW.apellido_paterno := btrim(NEW.apellido_paterno);
    NEW.apellido_materno := btrim(NEW.apellido_materno);
    NEW.nacionalidad := btrim(NEW.nacionalidad);
    NEW.curp := upper(btrim(NEW.curp));
    NEW.rfc := upper(btrim(NEW.rfc));
    NEW.correo_electronico := lower(btrim(NEW.correo_electronico));
    IF NEW.fecha_nacimiento > (hoy - INTERVAL '18 years')::date THEN
        RAISE EXCEPTION 'El cliente debe tener al menos 18 años y la fecha no puede ser futura'
            USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' AND (NEW.id IS DISTINCT FROM OLD.id
        OR NEW.curp IS DISTINCT FROM OLD.curp OR NEW.rfc IS DISTINCT FROM OLD.rfc) THEN
        RAISE EXCEPTION 'No se permite modificar ID, CURP o RFC' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
