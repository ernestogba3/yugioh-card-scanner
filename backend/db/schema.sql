-- Extensión para búsqueda fuzzy por similitud de trigramas.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Catálogo principal de cartas.
CREATE TABLE IF NOT EXISTS cards (
    id              BIGINT PRIMARY KEY,
    name            TEXT NOT NULL,
    name_es         TEXT,
    konami_id       INTEGER,
    type            TEXT,
    frame_type      TEXT,
    description     TEXT,
    atk             INTEGER,
    def             INTEGER,
    level           INTEGER,
    race            TEXT,
    attribute       TEXT,
    archetype       TEXT,
    image_url       TEXT,
    image_url_small TEXT
);

-- Migraciones: añaden columnas si la tabla ya existía de una importación anterior.
ALTER TABLE cards ADD COLUMN IF NOT EXISTS name_es TEXT;
ALTER TABLE cards ADD COLUMN IF NOT EXISTS konami_id INTEGER;

-- Índices GIN de trigramas: hacen rápidas las búsquedas fuzzy (name % 'texto').
-- Uno por idioma, porque el OCR puede leer la carta en inglés o en español.
CREATE INDEX IF NOT EXISTS idx_cards_name_trgm ON cards USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_cards_name_es_trgm ON cards USING gin (name_es gin_trgm_ops);
-- Índice para cruzar por el id oficial de Konami al rellenar name_es.
CREATE INDEX IF NOT EXISTS idx_cards_konami ON cards(konami_id);

-- Relación carta <-> sets en los que aparece.
CREATE TABLE IF NOT EXISTS card_sets (
    card_id    BIGINT REFERENCES cards(id) ON DELETE CASCADE,
    set_name   TEXT,
    set_code   TEXT,
    set_rarity TEXT
);
CREATE INDEX IF NOT EXISTS idx_card_sets_card ON card_sets(card_id);
CREATE INDEX IF NOT EXISTS idx_card_sets_name ON card_sets(set_name);

-- Resumen de cada set con su número total de cartas (para los porcentajes).
CREATE TABLE IF NOT EXISTS sets (
    set_name     TEXT PRIMARY KEY,
    set_code     TEXT,
    num_of_cards INTEGER,
    tcg_date     DATE
);
