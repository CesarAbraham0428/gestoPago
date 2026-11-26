-- El historial filtra por usuario y ordena por id descendente.
CREATE INDEX IF NOT EXISTS idx_pagos_usuario_id ON pagos (usuario_id, id DESC);
