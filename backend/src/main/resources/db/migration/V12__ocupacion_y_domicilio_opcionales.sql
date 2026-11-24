-- Ocupación, empresa, ingreso mensual y domicilio pasan a ser opcionales en el registro;
-- el cliente puede capturarlos después desde "Editar perfil". Los CHECK existentes siguen
-- vigentes: aceptan NULL y rechazan textos vacíos o ingresos no positivos.
ALTER TABLE clientes ALTER COLUMN ocupacion DROP NOT NULL;
ALTER TABLE clientes ALTER COLUMN empresa DROP NOT NULL;
ALTER TABLE clientes ALTER COLUMN ingreso_mensual DROP NOT NULL;

-- El alta ya no exige domicilio; sí sigue exigiendo usuario y al menos una cuenta.
CREATE OR REPLACE FUNCTION validar_registro_completo() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM usuarios WHERE cliente_id = NEW.id) THEN
        RAISE EXCEPTION 'El cliente debe tener un usuario' USING ERRCODE = '23514';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM cuentas WHERE cliente_id = NEW.id) THEN
        RAISE EXCEPTION 'El cliente debe tener al menos una cuenta' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;
