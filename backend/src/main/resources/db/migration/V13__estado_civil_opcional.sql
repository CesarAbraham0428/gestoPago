-- El estado civil es opcional en el registro; se puede capturar despues en "Editar perfil".
ALTER TABLE clientes ALTER COLUMN estado_civil DROP NOT NULL;
