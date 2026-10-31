-- GestoPago exige guardar las peticiones y respuestas intercambiadas (log de evidencia).
-- peticion guarda el JSON exacto enviado a sendTx: confirmTx debe reenviar exactamente los mismos parámetros.
ALTER TABLE pagos ADD COLUMN IF NOT EXISTS peticion text;
ALTER TABLE pagos ADD COLUMN IF NOT EXISTS respuesta text;
ALTER TABLE pagos ADD COLUMN IF NOT EXISTS respuesta_confirmacion text;

-- telefono siempre se envía: con tipoFront 2 va fijo "1111111111" y en artículos canjeables "#1111111111".
ALTER TABLE pagos ALTER COLUMN telefono TYPE varchar(20);
ALTER TABLE pagos DROP CONSTRAINT IF EXISTS ck_pagos_telefono;
ALTER TABLE pagos ADD CONSTRAINT ck_pagos_telefono CHECK (telefono IS NULL OR telefono ~ '^#?[0-9]{10}$');
