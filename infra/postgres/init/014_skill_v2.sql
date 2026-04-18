CREATE TABLE IF NOT EXISTS skill_definition (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    icon TEXT NOT NULL DEFAULT '⚡',
    category TEXT NOT NULL DEFAULT 'general',
    status TEXT NOT NULL DEFAULT 'enabled',
    source_type TEXT NOT NULL DEFAULT 'db',
    current_version_id BIGINT,
    run_count INTEGER NOT NULL DEFAULT 0,
    last_run_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS skill_version (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill_definition(id) ON DELETE CASCADE,
    version_no INTEGER NOT NULL,
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    prompt_template TEXT NOT NULL DEFAULT '',
    prompt_content TEXT NOT NULL DEFAULT '',
    script_content TEXT NOT NULL DEFAULT '',
    variables JSONB NOT NULL DEFAULT '[]'::jsonb,
    tools JSONB NOT NULL DEFAULT '[]'::jsonb,
    enabled_tools TEXT NOT NULL DEFAULT '',
    checksum TEXT NOT NULL DEFAULT '',
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_skill_version_skill_version_no UNIQUE (skill_id, version_no)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_skill_definition_current_version'
    ) THEN
        ALTER TABLE skill_definition
            ADD CONSTRAINT fk_skill_definition_current_version
            FOREIGN KEY (current_version_id)
            REFERENCES skill_version(id)
            ON DELETE SET NULL;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS skill_runtime_metrics (
    skill_id BIGINT PRIMARY KEY REFERENCES skill_definition(id) ON DELETE CASCADE,
    run_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    failure_count INTEGER NOT NULL DEFAULT 0,
    last_run_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_skill_definition_category_status
    ON skill_definition(category, status);

CREATE INDEX IF NOT EXISTS idx_skill_runtime_metrics_last_run
    ON skill_runtime_metrics(last_run_at DESC NULLS LAST);

CREATE INDEX IF NOT EXISTS idx_skill_version_skill_published
    ON skill_version(skill_id, is_published DESC, version_no DESC, id DESC);

INSERT INTO skill_definition (
    id, name, description, icon, category, status, source_type, run_count, last_run_at, created_at, updated_at
)
SELECT
    s.id,
    s.name,
    COALESCE(s.description, ''),
    COALESCE(s.icon, '⚡'),
    COALESCE(s.category, 'general'),
    COALESCE(s.status, 'enabled'),
    'legacy_db',
    COALESCE(s.run_count, 0),
    s.last_run_at,
    COALESCE(s.created_at, NOW()),
    COALESCE(s.updated_at, NOW())
FROM skill s
ON CONFLICT (id) DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('skill_definition', 'id'),
    COALESCE((SELECT MAX(id) FROM skill_definition), 1),
    TRUE
);

INSERT INTO skill_version (
    skill_id, version_no, is_published, prompt_template, prompt_content, script_content,
    variables, tools, enabled_tools, checksum, published_at, created_at, updated_at
)
SELECT
    s.id,
    1,
    TRUE,
    COALESCE(s.prompt_template, ''),
    COALESCE(s.prompt_template, ''),
    '',
    COALESCE(s.variables, '[]'::jsonb),
    COALESCE(s.tools, '[]'::jsonb),
    COALESCE(s.enabled_tools, ''),
    md5(
        COALESCE(s.prompt_template, '') || '|' ||
        COALESCE(s.enabled_tools, '') || '|' ||
        COALESCE(s.variables::text, '[]') || '|' ||
        COALESCE(s.tools::text, '[]')
    ),
    COALESCE(s.last_run_at, s.updated_at, NOW()),
    COALESCE(s.created_at, NOW()),
    COALESCE(s.updated_at, NOW())
FROM skill s
WHERE NOT EXISTS (
    SELECT 1
    FROM skill_version sv
    WHERE sv.skill_id = s.id
);

UPDATE skill_definition sd
SET current_version_id = latest.id
FROM (
    SELECT DISTINCT ON (skill_id)
        skill_id,
        id
    FROM skill_version
    ORDER BY skill_id, is_published DESC, version_no DESC, id DESC
) latest
WHERE sd.id = latest.skill_id
  AND (sd.current_version_id IS NULL OR sd.current_version_id <> latest.id);

INSERT INTO skill_runtime_metrics (skill_id, run_count, success_count, failure_count, last_run_at, updated_at)
SELECT
    sd.id,
    COALESCE(sd.run_count, 0),
    0,
    0,
    sd.last_run_at,
    NOW()
FROM skill_definition sd
ON CONFLICT (skill_id) DO NOTHING;
