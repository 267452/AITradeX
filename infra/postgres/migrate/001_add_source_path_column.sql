-- 迁移脚本：为 knowledge_document 表添加 source_path 列
-- 运行此脚本修复已存在的数据库

-- 检查并添加 source_path 列
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'knowledge_document' AND column_name = 'source_path') THEN
        ALTER TABLE knowledge_document ADD COLUMN source_path TEXT;
        RAISE NOTICE 'Added source_path column to knowledge_document table';
    ELSE
        RAISE NOTICE 'source_path column already exists in knowledge_document table';
    END IF;
END $$;
