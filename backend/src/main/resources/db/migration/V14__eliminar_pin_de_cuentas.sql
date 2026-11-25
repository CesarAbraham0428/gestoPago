-- Los pagos se autorizan con el código TOTP de la app autenticadora del usuario: el PIN por cuenta ya no existe.
-- intentos_pin_fallidos y pin_bloqueado_hasta se conservan: ahora cuentan los códigos incorrectos al pagar.
ALTER TABLE cuentas DROP COLUMN IF EXISTS pin_hash;
