CREATE TABLE IF NOT EXISTS notification_channel (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    channel_type TEXT NOT NULL,
    config JSONB NOT NULL DEFAULT '{}',
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notification_channel_type
    ON notification_channel(channel_type);

INSERT INTO notification_channel (name, channel_type, config, enabled)
VALUES
    ('飞书 Webhook', 'feishu', '{"webhook_url": "", "secret": ""}', false),
    ('企业微信 Webhook', 'wecom', '{"webhook_url": "", "secret": ""}', false)
ON CONFLICT DO NOTHING;
