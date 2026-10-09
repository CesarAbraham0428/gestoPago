CREATE DATABASE gestopago
    WITH TEMPLATE template0
    ENCODING 'UTF8';

-----------

BEGIN;
SET LOCAL search_path TO public;

-- 1. Integracion con GestoPago: tokens y catalogo de productos.
CREATE TABLE gestopago_tokens (
    id                  SERIAL PRIMARY KEY,
    id_distribuidor     INTEGER         NOT NULL,
    codigo_dispositivo  VARCHAR(100)    NOT NULL,
    token               TEXT            NOT NULL,
    token_type          VARCHAR(50),
    expires_in          BIGINT,
    fecha_creacion      TIMESTAMP       NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMP       NOT NULL DEFAULT NOW(),
    activo              BOOLEAN         NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_gestopago_tokens UNIQUE (id_distribuidor, codigo_dispositivo)
);
CREATE TABLE producto (
    id SERIAL PRIMARY KEY,
    producto TEXT NOT NULL,
    servicio TEXT NOT NULL,
    id_servicio INTEGER NOT NULL,
    id_producto INTEGER NOT NULL,
    id_cat_tipo_servicio INTEGER,
    tipo_front INTEGER,
    has_digito_verificador BOOLEAN,
    tipo_referencia TEXT,
    precio NUMERIC(12,2),
    show_ayuda BOOLEAN,
    legend TEXT
);

-- 2. Tablas historicas:
CREATE TABLE personas (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(120),
    apellido_paterno VARCHAR(120),
    apellido_materno VARCHAR(120),
    correo VARCHAR(254),
    telefono VARCHAR(30)
);

CREATE TABLE registro (
    id SERIAL PRIMARY KEY,
    usuario VARCHAR(80) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    persona_id INTEGER NOT NULL UNIQUE REFERENCES personas(id) ON DELETE CASCADE,
    CONSTRAINT uk_registro_usuario UNIQUE (usuario)
);

CREATE UNIQUE INDEX uk_registro_usuario_ci ON registro (LOWER(usuario));

-- 3. Clientes: enumeraciones, funciones, tablas, secuencias, restricciones,
--    indices y triggers de integridad, auditoria y baja logica.
CREATE TYPE public.estado_civil_enum AS ENUM (
    'Soltero',
    'Casado',
    'Divorciado',
    'Viudo',
    'Union libre'
);

CREATE TYPE public.rol_enum AS ENUM (
    'Cliente',
    'Administrador'
);

CREATE TYPE public.sexo_enum AS ENUM (
    'Masculino',
    'Femenino'
);

-- Funciones de auditoria y reglas de negocio.
CREATE FUNCTION public.actualizar_auditoria() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.fecha_creacion := OLD.fecha_creacion;
    NEW.fecha_actualizacion := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

CREATE FUNCTION public.impedir_eliminacion_fisica() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION
        'No se permite eliminar físicamente registros de %. Utiliza baja lógica.',
        TG_TABLE_NAME
        USING ERRCODE = '23514';

    RETURN NULL;
END;
$$;

CREATE FUNCTION public.sincronizar_cliente() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.correo_electronico IS DISTINCT FROM OLD.correo_electronico THEN
        UPDATE usuarios
           SET correo = NEW.correo_electronico
         WHERE cliente_id = NEW.id;
    END IF;

    IF NOT NEW.activo THEN
        UPDATE usuarios
           SET activo = FALSE
         WHERE cliente_id = NEW.id
           AND activo = TRUE;

        UPDATE cuentas
           SET esta_activa = FALSE
         WHERE cliente_id = NEW.id
           AND esta_activa = TRUE;
    END IF;

    RETURN NEW;
END;
$$;

CREATE FUNCTION public.validar_cliente() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    hoy DATE := (CURRENT_TIMESTAMP AT TIME ZONE 'America/Mexico_City')::date;
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

CREATE FUNCTION public.validar_cuenta() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    cliente_activo BOOLEAN;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF NEW.id IS DISTINCT FROM OLD.id
           OR NEW.cliente_id IS DISTINCT FROM OLD.cliente_id
           OR NEW.numero_cuenta IS DISTINCT FROM OLD.numero_cuenta THEN
            RAISE EXCEPTION
                'No se permite modificar ID, cliente o número de cuenta'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    SELECT activo
      INTO cliente_activo
      FROM clientes
     WHERE id = NEW.cliente_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'El cliente no existe'
            USING ERRCODE = '23503';
    END IF;

    IF NEW.esta_activa AND NOT cliente_activo THEN
        RAISE EXCEPTION
            'Un cliente inactivo no puede tener cuentas activas'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE FUNCTION public.validar_propietario_domicilio() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.cliente_id IS DISTINCT FROM OLD.cliente_id THEN
        RAISE EXCEPTION
            'No se permite modificar ID o cliente del domicilio'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE FUNCTION public.validar_registro_completo() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM domicilios WHERE cliente_id = NEW.id
    ) THEN
        RAISE EXCEPTION 'El cliente debe tener un domicilio'
            USING ERRCODE = '23514';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM usuarios WHERE cliente_id = NEW.id
    ) THEN
        RAISE EXCEPTION 'El cliente debe tener un usuario'
            USING ERRCODE = '23514';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM cuentas WHERE cliente_id = NEW.id
    ) THEN
        RAISE EXCEPTION 'El cliente debe tener al menos una cuenta'
            USING ERRCODE = '23514';
    END IF;

    RETURN NULL;
END;
$$;

CREATE FUNCTION public.validar_usuario() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    cliente_activo BOOLEAN;
    correo_cliente VARCHAR(100);
BEGIN
    NEW.correo := lower(btrim(NEW.correo));

    IF TG_OP = 'UPDATE' THEN
        IF NEW.id IS DISTINCT FROM OLD.id
           OR NEW.cliente_id IS DISTINCT FROM OLD.cliente_id THEN
            RAISE EXCEPTION
                'No se permite modificar ID o cliente del usuario'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    SELECT activo, correo_electronico
      INTO cliente_activo, correo_cliente
      FROM clientes
     WHERE id = NEW.cliente_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'El cliente no existe'
            USING ERRCODE = '23503';
    END IF;

    IF NEW.correo IS DISTINCT FROM correo_cliente THEN
        RAISE EXCEPTION
            'El correo del usuario debe coincidir con el del cliente'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.activo AND NOT cliente_activo THEN
        RAISE EXCEPTION
            'Un cliente inactivo no puede tener un usuario activo'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

-- Tablas de clientes y relaciones.
CREATE TABLE public.clientes (
    id INTEGER GENERATED ALWAYS AS IDENTITY NOT NULL,
    primer_nombre VARCHAR(50) NOT NULL,
    segundo_nombre VARCHAR(50),
    apellido_paterno VARCHAR(50) NOT NULL,
    apellido_materno VARCHAR(50) NOT NULL,
    fecha_nacimiento DATE NOT NULL,
    curp VARCHAR(18) NOT NULL,
    rfc VARCHAR(13) NOT NULL,
    sexo public.sexo_enum NOT NULL,
    nacionalidad VARCHAR(100) DEFAULT 'Mexicana' NOT NULL,
    estado_civil public.estado_civil_enum NOT NULL,
    correo_electronico VARCHAR(100) NOT NULL,
    telefono_movil VARCHAR(10) NOT NULL,
    telefono_alternativo VARCHAR(10),
    ocupacion VARCHAR(250) NOT NULL,
    empresa VARCHAR(250) NOT NULL,
    ingreso_mensual NUMERIC(18,2) NOT NULL,
    rol public.rol_enum DEFAULT 'Cliente'::public.rol_enum NOT NULL,
    activo BOOLEAN DEFAULT true NOT NULL,
    fecha_creacion TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    fecha_actualizacion TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_clientes_apellido_materno CHECK (char_length(apellido_materno) BETWEEN 2 AND 50),
    CONSTRAINT ck_clientes_apellido_paterno CHECK (char_length(apellido_paterno) BETWEEN 2 AND 50),
    CONSTRAINT ck_clientes_correo CHECK (correo_electronico = lower(btrim(correo_electronico)) AND correo_electronico ~ '^[^[:space:]@]+@[^[:space:]@.]+(\.[^[:space:]@.]+)+$'),
    CONSTRAINT ck_clientes_curp_formato CHECK (curp ~ '^[A-Z][AEIOUX][A-Z]{2}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[HM](AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)[B-DF-HJ-NP-TV-Z]{3}[A-Z0-9][0-9]$'),
    CONSTRAINT ck_clientes_empresa CHECK (char_length(btrim(empresa)) > 0),
    CONSTRAINT ck_clientes_ingreso_mensual CHECK (ingreso_mensual > 0 AND ingreso_mensual <> 'NaN'::numeric),
    CONSTRAINT ck_clientes_ocupacion CHECK (char_length(btrim(ocupacion)) > 0),
    CONSTRAINT ck_clientes_primer_nombre CHECK (char_length(primer_nombre) BETWEEN 2 AND 50),
    CONSTRAINT ck_clientes_rfc_formato CHECK (rfc ~ '^[A-ZÑ&]{3,4}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[A-Z0-9]{3}$'),
    CONSTRAINT ck_clientes_segundo_nombre CHECK (segundo_nombre IS NULL OR char_length(segundo_nombre) BETWEEN 2 AND 50),
    CONSTRAINT ck_clientes_telefono_alternativo CHECK (telefono_alternativo IS NULL OR telefono_alternativo ~ '^[0-9]{10}$'),
    CONSTRAINT ck_clientes_telefono_movil CHECK (telefono_movil ~ '^[0-9]{10}$')
);

CREATE TABLE public.cuentas (
    id INTEGER GENERATED ALWAYS AS IDENTITY NOT NULL,
    cliente_id INTEGER NOT NULL,
    numero_cuenta VARCHAR(18) NOT NULL,
    saldo NUMERIC(18,2) DEFAULT 0 NOT NULL,
    esta_activa BOOLEAN DEFAULT true NOT NULL,
    fecha_creacion TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    fecha_actualizacion TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_cuentas_numero CHECK (numero_cuenta ~ '^[0-9]{18}$'),
    CONSTRAINT ck_cuentas_saldo CHECK (saldo >= 0 AND saldo <> 'NaN'::numeric)
);

CREATE TABLE public.domicilios (
    id INTEGER GENERATED ALWAYS AS IDENTITY NOT NULL,
    cliente_id INTEGER NOT NULL,
    calle TEXT NOT NULL,
    numero_exterior VARCHAR(50) NOT NULL,
    numero_interior VARCHAR(50),
    colonia TEXT NOT NULL,
    municipio VARCHAR(250) NOT NULL,
    estado VARCHAR(250) NOT NULL,
    codigo_postal VARCHAR(5) NOT NULL,
    pais VARCHAR(100) NOT NULL,
    fecha_creacion TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    fecha_actualizacion TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_domicilios_calle CHECK (char_length(btrim(calle)) > 0),
    CONSTRAINT ck_domicilios_codigo_postal CHECK (codigo_postal ~ '^[0-9]{5}$'),
    CONSTRAINT ck_domicilios_colonia CHECK (char_length(btrim(colonia)) > 0),
    CONSTRAINT ck_domicilios_estado CHECK (char_length(btrim(estado)) > 0),
    CONSTRAINT ck_domicilios_municipio CHECK (char_length(btrim(municipio)) > 0),
    CONSTRAINT ck_domicilios_numero_exterior CHECK (char_length(btrim(numero_exterior)) > 0),
    CONSTRAINT ck_domicilios_numero_interior CHECK (numero_interior IS NULL OR char_length(btrim(numero_interior)) > 0),
    CONSTRAINT ck_domicilios_pais CHECK (char_length(btrim(pais)) > 0)
);

CREATE SEQUENCE public.numero_cuenta_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 999999999999999999
    CACHE 1;

ALTER SEQUENCE public.numero_cuenta_seq OWNED BY public.cuentas.numero_cuenta;

CREATE TABLE public.usuarios (
    id INTEGER GENERATED ALWAYS AS IDENTITY NOT NULL,
    cliente_id INTEGER NOT NULL,
    correo VARCHAR(100) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    activo BOOLEAN DEFAULT true NOT NULL,
    fecha_creacion TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    fecha_actualizacion TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_usuarios_correo CHECK (correo = lower(btrim(correo)) AND correo ~ '^[^[:space:]@]+@[^[:space:]@.]+(\.[^[:space:]@.]+)+$'),
    CONSTRAINT ck_usuarios_password_bcrypt CHECK (password_hash ~ '^\$2[aby]\$(0[4-9]|[12][0-9]|3[01])\$[./A-Za-z0-9]{53}$')
);

ALTER TABLE public.cuentas ALTER COLUMN numero_cuenta SET DEFAULT lpad(nextval('public.numero_cuenta_seq')::text, 18, '0');

-- Claves primarias y restricciones de unicidad.
ALTER TABLE public.clientes
    ADD CONSTRAINT clientes_pkey PRIMARY KEY (id);

ALTER TABLE public.cuentas
    ADD CONSTRAINT cuentas_pkey PRIMARY KEY (id);

ALTER TABLE public.domicilios
    ADD CONSTRAINT domicilios_pkey PRIMARY KEY (id);

ALTER TABLE public.clientes
    ADD CONSTRAINT uq_clientes_curp UNIQUE (curp);

ALTER TABLE public.clientes
    ADD CONSTRAINT uq_clientes_rfc UNIQUE (rfc);

ALTER TABLE public.cuentas
    ADD CONSTRAINT uq_cuentas_numero UNIQUE (numero_cuenta);

ALTER TABLE public.domicilios
    ADD CONSTRAINT uq_domicilios_cliente UNIQUE (cliente_id);

ALTER TABLE public.usuarios
    ADD CONSTRAINT uq_usuarios_cliente UNIQUE (cliente_id);

ALTER TABLE public.usuarios
    ADD CONSTRAINT usuarios_pkey PRIMARY KEY (id);

-- Indices de busqueda y unicidad sin distincion de mayusculas.
CREATE INDEX idx_clientes_fecha_creacion ON public.clientes USING btree (fecha_creacion);

CREATE INDEX idx_cuentas_cliente ON public.cuentas USING btree (cliente_id);

CREATE UNIQUE INDEX uq_clientes_correo ON public.clientes USING btree (lower(correo_electronico));

CREATE UNIQUE INDEX uq_usuarios_correo ON public.usuarios USING btree (lower(correo));

-- Triggers: auditoria, baja logica, normalizacion y registro completo.
CREATE TRIGGER trg_auditoria_clientes BEFORE UPDATE ON public.clientes FOR EACH ROW EXECUTE FUNCTION public.actualizar_auditoria();

CREATE TRIGGER trg_auditoria_cuentas BEFORE UPDATE ON public.cuentas FOR EACH ROW EXECUTE FUNCTION public.actualizar_auditoria();

CREATE TRIGGER trg_auditoria_domicilios BEFORE UPDATE ON public.domicilios FOR EACH ROW EXECUTE FUNCTION public.actualizar_auditoria();

CREATE TRIGGER trg_auditoria_usuarios BEFORE UPDATE ON public.usuarios FOR EACH ROW EXECUTE FUNCTION public.actualizar_auditoria();

CREATE TRIGGER trg_no_eliminar_clientes BEFORE DELETE ON public.clientes FOR EACH ROW EXECUTE FUNCTION public.impedir_eliminacion_fisica();

CREATE TRIGGER trg_no_eliminar_cuentas BEFORE DELETE ON public.cuentas FOR EACH ROW EXECUTE FUNCTION public.impedir_eliminacion_fisica();

CREATE TRIGGER trg_no_eliminar_domicilios BEFORE DELETE ON public.domicilios FOR EACH ROW EXECUTE FUNCTION public.impedir_eliminacion_fisica();

CREATE TRIGGER trg_no_eliminar_usuarios BEFORE DELETE ON public.usuarios FOR EACH ROW EXECUTE FUNCTION public.impedir_eliminacion_fisica();

CREATE TRIGGER trg_propietario_domicilio BEFORE UPDATE ON public.domicilios FOR EACH ROW EXECUTE FUNCTION public.validar_propietario_domicilio();

CREATE CONSTRAINT TRIGGER trg_registro_completo AFTER INSERT ON public.clientes DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.validar_registro_completo();

CREATE TRIGGER trg_sincronizar_cliente AFTER UPDATE ON public.clientes FOR EACH ROW EXECUTE FUNCTION public.sincronizar_cliente();

CREATE TRIGGER trg_validar_cliente BEFORE INSERT OR UPDATE ON public.clientes FOR EACH ROW EXECUTE FUNCTION public.validar_cliente();

CREATE TRIGGER trg_validar_cuenta BEFORE INSERT OR UPDATE ON public.cuentas FOR EACH ROW EXECUTE FUNCTION public.validar_cuenta();

CREATE TRIGGER trg_validar_usuario BEFORE INSERT OR UPDATE ON public.usuarios FOR EACH ROW EXECUTE FUNCTION public.validar_usuario();

-- Claves foraneas. La baja es logica: las relaciones no se eliminan.
ALTER TABLE public.cuentas
    ADD CONSTRAINT fk_cuentas_cliente FOREIGN KEY (cliente_id) REFERENCES public.clientes(id) ON DELETE RESTRICT;

ALTER TABLE public.domicilios
    ADD CONSTRAINT fk_domicilios_cliente FOREIGN KEY (cliente_id) REFERENCES public.clientes(id) ON DELETE RESTRICT;

ALTER TABLE public.usuarios
    ADD CONSTRAINT fk_usuarios_cliente FOREIGN KEY (cliente_id) REFERENCES public.clientes(id) ON DELETE RESTRICT;

-- 4. Validaciones adicionales de nombres y nacionalidad.
ALTER TABLE public.clientes ADD CONSTRAINT ck_clientes_nacionalidad CHECK (length(btrim(nacionalidad)) > 0);
ALTER TABLE clientes ADD CONSTRAINT ck_clientes_nombres_letras CHECK (
    primer_nombre ~ '^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ ]+$'
    AND apellido_paterno ~ '^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ ]+$'
    AND apellido_materno ~ '^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ ]+$'
    AND (segundo_nombre IS NULL OR segundo_nombre ~ '^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ ]+$')
);

-- Ejecutar las validaciones diferidas antes de mostrar el mensaje de exito.
SET CONSTRAINTS ALL IMMEDIATE;

DO $$
BEGIN
    RAISE NOTICE 'Base de Datos creada correctamente';
END;
$$;

COMMIT;
