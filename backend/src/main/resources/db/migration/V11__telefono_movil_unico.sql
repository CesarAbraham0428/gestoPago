-- El teléfono móvil identifica a un cliente igual que el correo: no puede repetirse entre cuentas.
-- Si esta migración falla, hay teléfonos repetidos en "clientes" que deben depurarse antes de aplicarla.
CREATE UNIQUE INDEX IF NOT EXISTS uq_clientes_telefono_movil ON clientes (telefono_movil);
