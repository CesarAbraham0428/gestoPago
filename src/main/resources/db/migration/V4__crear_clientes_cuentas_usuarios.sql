-- Esquema de clientes. Si ya se ejecutó el script manual, se conserva.
-- V1/V2 mantienen las tablas de productos y tokens; V3 se conserva por historial.
DO $migration$
BEGIN
    IF to_regclass('public.clientes') IS NULL THEN
        EXECUTE $schema$
--
-- PostgreSQL database dump
--


-- Dumped from database version 16.11
-- Dumped by pg_dump version 16.11


--
-- Name: estado_civil_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.estado_civil_enum AS ENUM (
    'Soltero',
    'Casado',
    'Divorciado',
    'Viudo',
    'Union libre'
);


--
-- Name: nacionalidad_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.nacionalidad_enum AS ENUM (
    'Mexicana'
);


--
-- Name: rol_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.rol_enum AS ENUM (
    'Cliente',
    'Administrador'
);


--
-- Name: sexo_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.sexo_enum AS ENUM (
    'Masculino',
    'Femenino'
);


--
-- Name: actualizar_auditoria(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.actualizar_auditoria() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.fecha_creacion := OLD.fecha_creacion;
    NEW.fecha_actualizacion := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;


--
-- Name: impedir_eliminacion_fisica(); Type: FUNCTION; Schema: public; Owner: -
--

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


--
-- Name: sincronizar_cliente(); Type: FUNCTION; Schema: public; Owner: -
--

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


--
-- Name: validar_cliente(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validar_cliente() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.primer_nombre := btrim(NEW.primer_nombre);
    NEW.segundo_nombre := btrim(NEW.segundo_nombre);
    NEW.apellido_paterno := btrim(NEW.apellido_paterno);
    NEW.apellido_materno := btrim(NEW.apellido_materno);

    NEW.curp := upper(btrim(NEW.curp));
    NEW.rfc := upper(btrim(NEW.rfc));
    NEW.correo_electronico := lower(btrim(NEW.correo_electronico));

    IF NEW.fecha_nacimiento > CURRENT_DATE THEN
        RAISE EXCEPTION 'La fecha de nacimiento no puede ser futura'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.fecha_nacimiento >
        (CURRENT_DATE - INTERVAL '18 years')::DATE THEN
        RAISE EXCEPTION 'El cliente debe tener al menos 18 años'
            USING ERRCODE = '23514';
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF NEW.id IS DISTINCT FROM OLD.id
           OR NEW.curp IS DISTINCT FROM OLD.curp
           OR NEW.rfc IS DISTINCT FROM OLD.rfc THEN
            RAISE EXCEPTION 'No se permite modificar ID, CURP o RFC'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;


--
-- Name: validar_cuenta(); Type: FUNCTION; Schema: public; Owner: -
--

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


--
-- Name: validar_propietario_domicilio(); Type: FUNCTION; Schema: public; Owner: -
--

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


--
-- Name: validar_registro_completo(); Type: FUNCTION; Schema: public; Owner: -
--

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


--
-- Name: validar_usuario(); Type: FUNCTION; Schema: public; Owner: -
--

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




--
-- Name: clientes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clientes (
    id integer NOT NULL,
    primer_nombre character varying(50) NOT NULL,
    segundo_nombre character varying(50),
    apellido_paterno character varying(50) NOT NULL,
    apellido_materno character varying(50) NOT NULL,
    fecha_nacimiento date NOT NULL,
    curp character varying(18) NOT NULL,
    rfc character varying(13) NOT NULL,
    sexo public.sexo_enum NOT NULL,
    nacionalidad public.nacionalidad_enum DEFAULT 'Mexicana'::public.nacionalidad_enum NOT NULL,
    estado_civil public.estado_civil_enum NOT NULL,
    correo_electronico character varying(100) NOT NULL,
    telefono_movil character varying(10) NOT NULL,
    telefono_alternativo character varying(10),
    ocupacion character varying(250) NOT NULL,
    empresa character varying(250) NOT NULL,
    ingreso_mensual numeric(18,2) NOT NULL,
    rol public.rol_enum DEFAULT 'Cliente'::public.rol_enum NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    fecha_creacion timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    fecha_actualizacion timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_clientes_apellido_materno CHECK (((char_length((apellido_materno)::text) >= 2) AND (char_length((apellido_materno)::text) <= 50))),
    CONSTRAINT ck_clientes_apellido_paterno CHECK (((char_length((apellido_paterno)::text) >= 2) AND (char_length((apellido_paterno)::text) <= 50))),
    CONSTRAINT ck_clientes_correo CHECK ((((correo_electronico)::text = lower(btrim((correo_electronico)::text))) AND ((correo_electronico)::text ~ '^[^[:space:]@]+@[^[:space:]@.]+(\.[^[:space:]@.]+)+$'::text))),
    CONSTRAINT ck_clientes_curp_formato CHECK (((curp)::text ~ '^[A-Z][AEIOUX][A-Z]{2}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[HM](AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)[B-DF-HJ-NP-TV-Z]{3}[A-Z0-9][0-9]$'::text)),
    CONSTRAINT ck_clientes_empresa CHECK ((char_length(btrim((empresa)::text)) > 0)),
    CONSTRAINT ck_clientes_ingreso_mensual CHECK (((ingreso_mensual > (0)::numeric) AND (ingreso_mensual <> 'NaN'::numeric))),
    CONSTRAINT ck_clientes_ocupacion CHECK ((char_length(btrim((ocupacion)::text)) > 0)),
    CONSTRAINT ck_clientes_primer_nombre CHECK (((char_length((primer_nombre)::text) >= 2) AND (char_length((primer_nombre)::text) <= 50))),
    CONSTRAINT ck_clientes_rfc_formato CHECK (((rfc)::text ~ '^[A-ZÑ&]{3,4}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[A-Z0-9]{3}$'::text)),
    CONSTRAINT ck_clientes_segundo_nombre CHECK (((segundo_nombre IS NULL) OR ((char_length((segundo_nombre)::text) >= 2) AND (char_length((segundo_nombre)::text) <= 50)))),
    CONSTRAINT ck_clientes_telefono_alternativo CHECK (((telefono_alternativo IS NULL) OR ((telefono_alternativo)::text ~ '^[0-9]{10}$'::text))),
    CONSTRAINT ck_clientes_telefono_movil CHECK (((telefono_movil)::text ~ '^[0-9]{10}$'::text))
);


--
-- Name: clientes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.clientes ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.clientes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: cuentas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cuentas (
    id integer NOT NULL,
    cliente_id integer NOT NULL,
    numero_cuenta character varying(18) NOT NULL,
    saldo numeric(18,2) DEFAULT 0 NOT NULL,
    esta_activa boolean DEFAULT true NOT NULL,
    fecha_creacion timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    fecha_actualizacion timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_cuentas_numero CHECK (((numero_cuenta)::text ~ '^[0-9]{18}$'::text)),
    CONSTRAINT ck_cuentas_saldo CHECK (((saldo >= (0)::numeric) AND (saldo <> 'NaN'::numeric)))
);


--
-- Name: cuentas_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.cuentas ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.cuentas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: domicilios; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.domicilios (
    id integer NOT NULL,
    cliente_id integer NOT NULL,
    calle text NOT NULL,
    numero_exterior character varying(50) NOT NULL,
    numero_interior character varying(50),
    colonia text NOT NULL,
    municipio character varying(250) NOT NULL,
    estado character varying(250) NOT NULL,
    codigo_postal character varying(5) NOT NULL,
    pais character varying(100) NOT NULL,
    fecha_creacion timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    fecha_actualizacion timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_domicilios_calle CHECK ((char_length(btrim(calle)) > 0)),
    CONSTRAINT ck_domicilios_codigo_postal CHECK (((codigo_postal)::text ~ '^[0-9]{5}$'::text)),
    CONSTRAINT ck_domicilios_colonia CHECK ((char_length(btrim(colonia)) > 0)),
    CONSTRAINT ck_domicilios_estado CHECK ((char_length(btrim((estado)::text)) > 0)),
    CONSTRAINT ck_domicilios_municipio CHECK ((char_length(btrim((municipio)::text)) > 0)),
    CONSTRAINT ck_domicilios_numero_exterior CHECK ((char_length(btrim((numero_exterior)::text)) > 0)),
    CONSTRAINT ck_domicilios_numero_interior CHECK (((numero_interior IS NULL) OR (char_length(btrim((numero_interior)::text)) > 0))),
    CONSTRAINT ck_domicilios_pais CHECK ((char_length(btrim((pais)::text)) > 0))
);


--
-- Name: domicilios_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.domicilios ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.domicilios_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: numero_cuenta_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.numero_cuenta_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 999999999999999999
    CACHE 1;


--
-- Name: numero_cuenta_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.numero_cuenta_seq OWNED BY public.cuentas.numero_cuenta;


--
-- Name: usuarios; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuarios (
    id integer NOT NULL,
    cliente_id integer NOT NULL,
    correo character varying(100) NOT NULL,
    password_hash character varying(250) NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    fecha_creacion timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    fecha_actualizacion timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_usuarios_correo CHECK ((((correo)::text = lower(btrim((correo)::text))) AND ((correo)::text ~ '^[^[:space:]@]+@[^[:space:]@.]+(\.[^[:space:]@.]+)+$'::text))),
    CONSTRAINT ck_usuarios_password_bcrypt CHECK (((password_hash)::text ~ '^\$2[aby]\$(0[4-9]|[12][0-9]|3[01])\$[./A-Za-z0-9]{53}$'::text))
);


--
-- Name: usuarios_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.usuarios ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.usuarios_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: cuentas numero_cuenta; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cuentas ALTER COLUMN numero_cuenta SET DEFAULT lpad((nextval('public.numero_cuenta_seq'::regclass))::text, 18, '0'::text);


--
-- Name: clientes clientes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clientes
    ADD CONSTRAINT clientes_pkey PRIMARY KEY (id);


--
-- Name: cuentas cuentas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cuentas
    ADD CONSTRAINT cuentas_pkey PRIMARY KEY (id);


--
-- Name: domicilios domicilios_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.domicilios
    ADD CONSTRAINT domicilios_pkey PRIMARY KEY (id);


--
-- Name: clientes uq_clientes_curp; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clientes
    ADD CONSTRAINT uq_clientes_curp UNIQUE (curp);


--
-- Name: clientes uq_clientes_rfc; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clientes
    ADD CONSTRAINT uq_clientes_rfc UNIQUE (rfc);


--
-- Name: cuentas uq_cuentas_numero; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cuentas
    ADD CONSTRAINT uq_cuentas_numero UNIQUE (numero_cuenta);


--
-- Name: domicilios uq_domicilios_cliente; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.domicilios
    ADD CONSTRAINT uq_domicilios_cliente UNIQUE (cliente_id);


--
-- Name: usuarios uq_usuarios_cliente; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT uq_usuarios_cliente UNIQUE (cliente_id);


--
-- Name: usuarios usuarios_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT usuarios_pkey PRIMARY KEY (id);


--
-- Name: idx_clientes_fecha_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_clientes_fecha_creacion ON public.clientes USING btree (fecha_creacion);


--
-- Name: idx_cuentas_cliente; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cuentas_cliente ON public.cuentas USING btree (cliente_id);


--
-- Name: uq_clientes_correo; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_clientes_correo ON public.clientes USING btree (lower((correo_electronico)::text));


--
-- Name: uq_usuarios_correo; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_usuarios_correo ON public.usuarios USING btree (lower((correo)::text));


--
-- Name: clientes trg_auditoria_clientes; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_auditoria_clientes BEFORE UPDATE ON public.clientes FOR EACH ROW EXECUTE FUNCTION public.actualizar_auditoria();


--
-- Name: cuentas trg_auditoria_cuentas; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_auditoria_cuentas BEFORE UPDATE ON public.cuentas FOR EACH ROW EXECUTE FUNCTION public.actualizar_auditoria();


--
-- Name: domicilios trg_auditoria_domicilios; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_auditoria_domicilios BEFORE UPDATE ON public.domicilios FOR EACH ROW EXECUTE FUNCTION public.actualizar_auditoria();


--
-- Name: usuarios trg_auditoria_usuarios; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_auditoria_usuarios BEFORE UPDATE ON public.usuarios FOR EACH ROW EXECUTE FUNCTION public.actualizar_auditoria();


--
-- Name: clientes trg_no_eliminar_clientes; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_no_eliminar_clientes BEFORE DELETE ON public.clientes FOR EACH ROW EXECUTE FUNCTION public.impedir_eliminacion_fisica();


--
-- Name: cuentas trg_no_eliminar_cuentas; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_no_eliminar_cuentas BEFORE DELETE ON public.cuentas FOR EACH ROW EXECUTE FUNCTION public.impedir_eliminacion_fisica();


--
-- Name: domicilios trg_no_eliminar_domicilios; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_no_eliminar_domicilios BEFORE DELETE ON public.domicilios FOR EACH ROW EXECUTE FUNCTION public.impedir_eliminacion_fisica();


--
-- Name: usuarios trg_no_eliminar_usuarios; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_no_eliminar_usuarios BEFORE DELETE ON public.usuarios FOR EACH ROW EXECUTE FUNCTION public.impedir_eliminacion_fisica();


--
-- Name: domicilios trg_propietario_domicilio; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_propietario_domicilio BEFORE UPDATE ON public.domicilios FOR EACH ROW EXECUTE FUNCTION public.validar_propietario_domicilio();


--
-- Name: clientes trg_registro_completo; Type: TRIGGER; Schema: public; Owner: -
--

CREATE CONSTRAINT TRIGGER trg_registro_completo AFTER INSERT ON public.clientes DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.validar_registro_completo();


--
-- Name: clientes trg_sincronizar_cliente; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_sincronizar_cliente AFTER UPDATE ON public.clientes FOR EACH ROW EXECUTE FUNCTION public.sincronizar_cliente();


--
-- Name: clientes trg_validar_cliente; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validar_cliente BEFORE INSERT OR UPDATE ON public.clientes FOR EACH ROW EXECUTE FUNCTION public.validar_cliente();


--
-- Name: cuentas trg_validar_cuenta; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validar_cuenta BEFORE INSERT OR UPDATE ON public.cuentas FOR EACH ROW EXECUTE FUNCTION public.validar_cuenta();


--
-- Name: usuarios trg_validar_usuario; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validar_usuario BEFORE INSERT OR UPDATE ON public.usuarios FOR EACH ROW EXECUTE FUNCTION public.validar_usuario();


--
-- Name: cuentas fk_cuentas_cliente; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cuentas
    ADD CONSTRAINT fk_cuentas_cliente FOREIGN KEY (cliente_id) REFERENCES public.clientes(id) ON DELETE RESTRICT;


--
-- Name: domicilios fk_domicilios_cliente; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.domicilios
    ADD CONSTRAINT fk_domicilios_cliente FOREIGN KEY (cliente_id) REFERENCES public.clientes(id) ON DELETE RESTRICT;


--
-- Name: usuarios fk_usuarios_cliente; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT fk_usuarios_cliente FOREIGN KEY (cliente_id) REFERENCES public.clientes(id) ON DELETE RESTRICT;


--
-- PostgreSQL database dump complete
--


        $schema$;
    END IF;
END;
$migration$;
