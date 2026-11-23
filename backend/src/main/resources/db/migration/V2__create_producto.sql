CREATE TABLE IF NOT EXISTS producto (
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
