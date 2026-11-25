-- Nombres y apellidos: de 3 a 50 caracteres. NOT VALID: no revalida filas anteriores (que pudieran tener 2),
-- pero toda alta o modificación nueva sí debe cumplir.
ALTER TABLE clientes DROP CONSTRAINT ck_clientes_primer_nombre;
ALTER TABLE clientes DROP CONSTRAINT ck_clientes_segundo_nombre;
ALTER TABLE clientes DROP CONSTRAINT ck_clientes_apellido_paterno;
ALTER TABLE clientes DROP CONSTRAINT ck_clientes_apellido_materno;
ALTER TABLE clientes ADD CONSTRAINT ck_clientes_primer_nombre
    CHECK (char_length(primer_nombre) BETWEEN 3 AND 50) NOT VALID;
ALTER TABLE clientes ADD CONSTRAINT ck_clientes_segundo_nombre
    CHECK (segundo_nombre IS NULL OR char_length(segundo_nombre) BETWEEN 3 AND 50) NOT VALID;
ALTER TABLE clientes ADD CONSTRAINT ck_clientes_apellido_paterno
    CHECK (char_length(apellido_paterno) BETWEEN 3 AND 50) NOT VALID;
ALTER TABLE clientes ADD CONSTRAINT ck_clientes_apellido_materno
    CHECK (char_length(apellido_materno) BETWEEN 3 AND 50) NOT VALID;

-- Calle y colonia: máximo 100 caracteres (antes text sin límite).
ALTER TABLE domicilios ALTER COLUMN calle TYPE varchar(100);
ALTER TABLE domicilios ALTER COLUMN colonia TYPE varchar(100);
