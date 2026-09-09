export type KnowledgeDoc = {
  id: string;
  name: string;
  category: "code" | "manual" | "case";
  region: string;
  specialty: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  enabled: boolean;
};

export type Project = {
  id: string;
  name: string;
  bridgeType: string;
  region: string;
  applicableDate: string;
  strategy: "original" | "current-review";
  span: string;
  material: string;
  status: "modeling" | "validating" | "calibrating" | "done";
  owner: string;
  updatedAt: string;
};

export type InquiryMessage = {
  role: "user" | "agent";
  text: string;
  time: string;
};

export type InquiryThread = {
  id: string;
  title: string;
  updatedAt: string;
  messages: InquiryMessage[];
};

export type ModelTask = {
  id: string;
  title: string;
  status: "running" | "waiting" | "done" | "failed";
  version: string;
  startedAt: string;
  steps: { time: string; text: string }[];
};

export type ModelVersion = {
  version: string;
  note: string;
  t1: string;
  massPart: string;
  createdAt: string;
};

export type Finding = {
  id: string;
  level: "L1" | "L2" | "L3";
  title: string;
  expect: string;
  actual: string;
  status: "open" | "resolved";
};

export type QueueJob = {
  id: string;
  project: string;
  type: string;
  user: string;
  waitMin: number;
  status: "running" | "queued";
};

export const currentUser = {
  name: "张明远",
  role: "建模工程师",
  dept: "桥梁设计一所",
};

export const project: Project = {
  id: "p-huming",
  name: "沪闵高架连续梁（示例）",
  bridgeType: "预应力混凝土连续梁",
  region: "上海市",
  applicableDate: "2004-06",
  strategy: "original",
  span: "40 + 60 + 40 m",
  material: "C50",
  status: "validating",
  owner: "张明远",
  updatedAt: "今天 18:20",
};

export const knowledgeCatalog: KnowledgeDoc[] = [
  {
    id: "d1",
    name: "公路钢筋混凝土及预应力混凝土桥涵设计规范 JTG D62-2004",
    category: "code",
    region: "国家",
    specialty: "桥梁",
    effectiveFrom: "2004-10",
    effectiveTo: "2018-10",
    enabled: true,
  },
  {
    id: "d2",
    name: "上海市桥梁抗震设计规范（适用 2004 时点）",
    category: "code",
    region: "上海市",
    specialty: "抗震",
    effectiveFrom: "2003-01",
    effectiveTo: "2013-12",
    enabled: true,
  },
  {
    id: "d3",
    name: "公路桥涵设计通用规范 JTG D60-2004",
    category: "code",
    region: "国家",
    specialty: "桥梁",
    effectiveFrom: "2004-10",
    effectiveTo: "2015-12",
    enabled: true,
  },
  {
    id: "d4",
    name: "混凝土结构设计规范 GB 50010-2002",
    category: "code",
    region: "国家",
    specialty: "混凝土",
    effectiveFrom: "2002-04",
    effectiveTo: "2011-06",
    enabled: false,
  },
  {
    id: "d5",
    name: "SAP2000 分析参考手册 v21",
    category: "manual",
    region: "通用",
    specialty: "求解器",
    effectiveFrom: "2021-01",
    effectiveTo: null,
    enabled: true,
  },
  {
    id: "d7",
    name: "连续梁桥 SAP2000 建模指导",
    category: "manual",
    region: "通用",
    specialty: "建模流程",
    effectiveFrom: "2023-03",
    effectiveTo: null,
    enabled: true,
  },
  {
    id: "d8",
    name: "预应力钢束与施工阶段建模说明",
    category: "manual",
    region: "通用",
    specialty: "建模流程",
    effectiveFrom: "2022-11",
    effectiveTo: null,
    enabled: false,
  },
  {
    id: "d6",
    name: "沪杭某连续梁模态标定（内部）",
    category: "case",
    region: "上海市",
    specialty: "模态",
    effectiveFrom: "2019-01",
    effectiveTo: null,
    enabled: false,
  },
  {
    id: "d9",
    name: "既有桥支座更换建模纪要（内部）",
    category: "case",
    region: "上海市",
    specialty: "支座",
    effectiveFrom: "2021-06",
    effectiveTo: null,
    enabled: false,
  },
];

export const knowledgeGroups: {
  key: KnowledgeDoc["category"];
  title: string;
  hint: string;
}[] = [
  { key: "code", title: "标准规范", hint: "国家规范与地方规范" },
  { key: "manual", title: "建模指导手册", hint: "SAP2000 与建模流程" },
  { key: "case", title: "工程案例", hint: "内部经验，项目中默认不启用" },
];

export const pendingSuggestion = {
  docId: "d4",
  reason: "材料验算需要混凝土本构与限值条文，当前启用集未包含对应时点的混凝土规范。",
  task: "T-12 验证 V1 材料完备性",
};

export const inquiryThreads: InquiryThread[] = [
  {
    id: "q1",
    title: "跨径与材料确认",
    updatedAt: "18:04",
    messages: [
      { role: "user", text: "这座桥的跨径组合和主梁材料是什么？", time: "18:02" },
      {
        role: "agent",
        text: "按项目对象：跨径 40+60+40 m，主梁 C50。依据来自项目概览，不是规范检索。",
        time: "18:03",
      },
    ],
  },
  {
    id: "q2",
    title: "当前启用了哪些规范",
    updatedAt: "17:40",
    messages: [
      { role: "user", text: "这个项目现在能检索哪些规范？", time: "17:38" },
      {
        role: "agent",
        text: "启用集含 JTG D62-2004、JTG D60-2004、上海抗震（2004 时点）及 SAP2000 手册。GB 50010-2002 尚未启用。",
        time: "17:39",
      },
    ],
  },
];

export const modelTasks: ModelTask[] = [
  {
    id: "T-12",
    title: "验证 V1 几何与完备性",
    status: "running",
    version: "V1",
    startedAt: "17:55",
    steps: [
      { time: "17:55", text: "读取 IR 快照 V1" },
      { time: "17:56", text: "L1 跨径链、支座位置通过" },
      { time: "18:01", text: "L2 发现材料条文缺口，已暂停并建议启用 GB 50010-2002" },
    ],
  },
  {
    id: "T-08",
    title: "生成初始连续梁模型",
    status: "done",
    version: "V1",
    startedAt: "16:20",
    steps: [
      { time: "16:20", text: "IR 已确认" },
      { time: "16:28", text: "编译器写入 SAP2000，静力 + 模态完成" },
      { time: "16:31", text: "结果表入库，版本 V1" },
    ],
  },
];

export const modelVersions: ModelVersion[] = [
  {
    version: "V1",
    note: "初始连续梁，支座按图纸布置",
    t1: "1.86 s",
    massPart: "X 91% / Y 88%",
    createdAt: "今天 16:31",
  },
];

export const findings: Finding[] = [
  {
    id: "F-03",
    level: "L2",
    title: "混凝土规范未在启用集中",
    expect: "材料验算可引用 GB 50010 对应时点条文",
    actual: "启用集无混凝土规范正文",
    status: "open",
  },
  {
    id: "F-02",
    level: "L3",
    title: "Y 向质量参与系数偏低",
    expect: "≥ 90%",
    actual: "88%",
    status: "open",
  },
  {
    id: "F-01",
    level: "L1",
    title: "跨径链与图纸一致",
    expect: "40+60+40 m",
    actual: "40+60+40 m",
    status: "resolved",
  },
];

export const drawings = [
  { name: "总体布置图.pdf", pages: 6, status: "已抽取", confidence: "高" },
  { name: "主梁断面.pdf", pages: 3, status: "待确认", confidence: "中" },
  { name: "墩台与支座详图.pdf", pages: 8, status: "已抽取", confidence: "高" },
];

export const members = [
  { name: "张明远", role: "建模工程师", access: "可写任务" },
  { name: "李衡", role: "项目负责人", access: "启用知识 / 批准校准" },
  { name: "王秋实", role: "只读审查", access: "只读" },
];

export const queueJobs: QueueJob[] = [
  {
    id: "J-204",
    project: "沪闵高架连续梁（示例）",
    type: "模态分析",
    user: "张明远",
    waitMin: 0,
    status: "running",
  },
  {
    id: "J-205",
    project: "嘉闵高架匝道桥",
    type: "反应谱",
    user: "陈屿",
    waitMin: 12,
    status: "queued",
  },
];

export const otherProjects: Project[] = [
  project,
  {
    id: "p-jiamin",
    name: "嘉闵高架匝道桥",
    bridgeType: "连续梁",
    region: "上海市",
    applicableDate: "2016-03",
    strategy: "original",
    span: "30 + 40 + 30 m",
    material: "C40",
    status: "modeling",
    owner: "陈屿",
    updatedAt: "昨天",
  },
  {
    id: "p-suzhou",
    name: "苏州河人行桥方案",
    bridgeType: "钢箱梁",
    region: "上海市",
    applicableDate: "2022-09",
    strategy: "current-review",
    span: "68 m",
    material: "Q345qD",
    status: "done",
    owner: "张明远",
    updatedAt: "08-12",
  },
];
