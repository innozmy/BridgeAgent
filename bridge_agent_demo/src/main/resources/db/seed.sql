-- 示例案例：沪闵高架连续梁（左幅）
-- 演示：建项主数据 + 识图后的两联跨径 + 一份图纸元数据

USE bridge_agent;

INSERT INTO project (
  name, carriageway, code, intro, region, opened_on,
  code_strategy, girder_type, layout_type, material, status
) VALUES (
  '沪闵高架连续梁（示例）',
  'left',
  'HM-K12',
  '上海市既有高架梁式桥示例。跨径由图纸识别写入，主梁形式为项目级，可通过会话或 CAD 调整。',
  '上海市',
  '2004-06-01',
  'at_opening',
  '预应力混凝土箱梁',
  '连续',
  'C50',
  'validating'
);

SET @pid = LAST_INSERT_ID();

INSERT INTO project_unit (project_id, seq, spans_m, length_m, source) VALUES
  (@pid, 1, CAST('[40, 60, 40]' AS JSON), 140.000, 'drawing'),
  (@pid, 2, CAST('[30, 30, 30]' AS JSON),  90.000, 'drawing');

INSERT INTO project_file (
  project_id, kind, original_name, storage_path, sha256,
  mime_type, size_bytes, parse_status
) VALUES (
  @pid,
  'drawing',
  '沪闵高架-左幅-总体布置图.pdf',
  'files/1/drawings/huming-left-general.pdf',
  'a3c8f1e92b4d7e6a0158c9d4f2b7e31a6c8d5f0b9e4a217c3d6f8b1e0a5c9472',
  'application/pdf',
  2485760,
  'parsed'
);

SELECT @pid AS inserted_project_id;
