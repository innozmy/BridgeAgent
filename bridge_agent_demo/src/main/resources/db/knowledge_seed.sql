-- 公司知识总库种子（可对已有库重复执行：按名称去重）
-- 沪闵示例（id=1）预启用 2004 时点的规范 + 两本手册；案例与混规不启用。
-- D62-2018 用来演示「主标签显示最新、下拉切换年版」。

USE bridge_agent;

INSERT INTO knowledge_document (
  name, category, family_code, region, specialty,
  effective_from, effective_to, parse_status
)
SELECT '公路钢筋混凝土及预应力混凝土桥涵设计规范 JTG D62-2004',
       'code', 'JTG D62', '国家', '桥梁', '2004-10-01', '2018-10-31', 'unparsed'
WHERE NOT EXISTS (
  SELECT 1 FROM knowledge_document WHERE name = '公路钢筋混凝土及预应力混凝土桥涵设计规范 JTG D62-2004'
);

INSERT INTO knowledge_document (
  name, category, family_code, region, specialty,
  effective_from, effective_to, parse_status
)
SELECT '公路钢筋混凝土及预应力混凝土桥涵设计规范 JTG 3362-2018',
       'code', 'JTG D62', '国家', '桥梁', '2018-11-01', NULL, 'unparsed'
WHERE NOT EXISTS (
  SELECT 1 FROM knowledge_document WHERE name = '公路钢筋混凝土及预应力混凝土桥涵设计规范 JTG 3362-2018'
);

INSERT INTO knowledge_document (
  name, category, family_code, region, specialty,
  effective_from, effective_to, parse_status
)
SELECT '上海市桥梁抗震设计规范（适用 2004 时点）',
       'code', NULL, '上海市', '抗震', '2003-01-01', '2013-12-31', 'unparsed'
WHERE NOT EXISTS (
  SELECT 1 FROM knowledge_document WHERE name = '上海市桥梁抗震设计规范（适用 2004 时点）'
);

INSERT INTO knowledge_document (
  name, category, family_code, region, specialty,
  effective_from, effective_to, parse_status
)
SELECT '公路桥涵设计通用规范 JTG D60-2004',
       'code', 'JTG D60', '国家', '桥梁', '2004-10-01', '2015-12-31', 'unparsed'
WHERE NOT EXISTS (
  SELECT 1 FROM knowledge_document WHERE name = '公路桥涵设计通用规范 JTG D60-2004'
);

INSERT INTO knowledge_document (
  name, category, family_code, region, specialty,
  effective_from, effective_to, parse_status
)
SELECT '混凝土结构设计规范 GB 50010-2002',
       'code', 'GB 50010', '国家', '混凝土', '2002-04-01', '2011-06-30', 'unparsed'
WHERE NOT EXISTS (
  SELECT 1 FROM knowledge_document WHERE name = '混凝土结构设计规范 GB 50010-2002'
);

INSERT INTO knowledge_document (
  name, category, family_code, region, specialty,
  effective_from, effective_to, parse_status
)
SELECT 'SAP2000 分析参考手册 v21',
       'manual', NULL, '通用', '求解器', '2021-01-01', NULL, 'unparsed'
WHERE NOT EXISTS (
  SELECT 1 FROM knowledge_document WHERE name = 'SAP2000 分析参考手册 v21'
);

INSERT INTO knowledge_document (
  name, category, family_code, region, specialty,
  effective_from, effective_to, parse_status
)
SELECT '连续梁桥 SAP2000 建模指导',
       'manual', NULL, '通用', '建模流程', '2023-03-01', NULL, 'unparsed'
WHERE NOT EXISTS (
  SELECT 1 FROM knowledge_document WHERE name = '连续梁桥 SAP2000 建模指导'
);

INSERT INTO knowledge_document (
  name, category, family_code, region, specialty,
  effective_from, effective_to, parse_status
)
SELECT '预应力钢束与施工阶段建模说明',
       'manual', NULL, '通用', '建模流程', '2022-11-01', NULL, 'unparsed'
WHERE NOT EXISTS (
  SELECT 1 FROM knowledge_document WHERE name = '预应力钢束与施工阶段建模说明'
);

INSERT INTO knowledge_document (
  name, category, family_code, region, specialty,
  effective_from, effective_to, parse_status
)
SELECT '沪杭某连续梁模态标定（内部）',
       'case', NULL, '上海市', '模态', '2019-01-01', NULL, 'unparsed'
WHERE NOT EXISTS (
  SELECT 1 FROM knowledge_document WHERE name = '沪杭某连续梁模态标定（内部）'
);

INSERT INTO knowledge_document (
  name, category, family_code, region, specialty,
  effective_from, effective_to, parse_status
)
SELECT '既有桥支座更换建模纪要（内部）',
       'case', NULL, '上海市', '支座', '2021-06-01', NULL, 'unparsed'
WHERE NOT EXISTS (
  SELECT 1 FROM knowledge_document WHERE name = '既有桥支座更换建模纪要（内部）'
);

INSERT INTO project_knowledge (project_id, document_id)
SELECT p.id, d.id
FROM project p
JOIN knowledge_document d ON d.name IN (
  '公路钢筋混凝土及预应力混凝土桥涵设计规范 JTG D62-2004',
  '上海市桥梁抗震设计规范（适用 2004 时点）',
  '公路桥涵设计通用规范 JTG D60-2004',
  'SAP2000 分析参考手册 v21',
  '连续梁桥 SAP2000 建模指导'
)
WHERE p.name = '沪闵高架连续梁（示例）'
  AND NOT EXISTS (
    SELECT 1 FROM project_knowledge pk
    WHERE pk.project_id = p.id AND pk.document_id = d.id
  );
