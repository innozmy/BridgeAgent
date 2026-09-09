export type ApiResult<T> = {
  code: number;
  msg: string;
  data: T;
};

export type FieldProvenance = {
  source?: string | null;
  drawingValue?: string | null;
};

export type ProjectFieldMeta = {
  code?: FieldProvenance | null;
  girderType?: FieldProvenance | null;
  layoutType?: FieldProvenance | null;
  material?: FieldProvenance | null;
  region?: FieldProvenance | null;
  spansM?: FieldProvenance | null;
  gaps?: string[] | null;
  hasLayoutPages?: boolean | null;
};

export type ProjectRecord = {
  id: number;
  name: string;
  carriageway: string;
  code: string | null;
  intro: string | null;
  region: string | null;
  openedOn: string | null;
  codeStrategy: string | null;
  girderType: string | null;
  layoutType: string | null;
  material: string | null;
  fieldMeta?: ProjectFieldMeta | null;
  status: string;
  /** 行级乐观锁，保存概览时带回 */
  version?: number;
  createdAt: string;
  updatedAt: string;
};

export type ProjectUnitColumn = {
  id?: number;
  supportId?: number;
  seq: number;
  side?: string | null;
  heightM?: number | null;
  source?: string | null;
};

export type ProjectUnitSupport = {
  id?: number;
  unitId?: number;
  seq: number;
  code?: string | null;
  kind?: string | null;
  source?: string | null;
  columns?: ProjectUnitColumn[];
};

export type ProjectUnit = {
  id: number;
  projectId: number;
  seq: number;
  spansM: number[];
  lengthM: number;
  source: string;
  supports?: ProjectUnitSupport[];
};

export type ProjectParam = {
  id?: number;
  projectId?: number;
  paramKey: string;
  label: string;
  valueText?: string | null;
  unit?: string | null;
  source?: string | null;
  version?: number;
};

export type ProjectFile = {
  id: number;
  projectId: number;
  kind: string;
  originalName: string;
  storagePath: string;
  sha256: string;
  mimeType: string | null;
  sizeBytes: number;
  parseStatus: string;
  createdAt: string;
};

export type ProjectDetail = ProjectRecord & {
  units: ProjectUnit[];
  params?: ProjectParam[];
  files: ProjectFile[];
};

export type ProjectCreateBody = {
  name: string;
  carriageway: string;
  code?: string;
  intro?: string;
  region?: string;
  openedOn?: string;
  codeStrategy?: string;
};

export type ProjectUpdateBody = {
  name?: string;
  carriageway?: string;
  code?: string;
  intro?: string;
  region?: string;
  openedOn?: string | null;
  codeStrategy?: string;
  girderType?: string;
  layoutType?: string;
  material?: string;
  version?: number;
};

export type ProjectUnitBatchBody = {
  version?: number;
  units: {
    seq: number;
    spansM: number[];
    source?: string;
    supports?: {
      seq: number;
      code?: string | null;
      kind?: string | null;
      source?: string;
      columns?: {
        seq: number;
        side?: string | null;
        heightM?: number | null;
        source?: string;
      }[];
    }[];
  }[];
};

export type ProjectParamBatchBody = {
  version?: number;
  items: {
    paramKey: string;
    label: string;
    valueText?: string;
    unit?: string;
    source?: string;
  }[];
};

export type FileUploadItem = {
  file: ProjectFile;
  duplicate: boolean;
};

export type FileBatchUpload = {
  saved: FileUploadItem[];
  errors: string[];
};

export type KnowledgeDocument = {
  id: number;
  name: string;
  category: "code" | "manual" | "case";
  familyCode: string | null;
  region: string | null;
  specialty: string | null;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  originalName: string | null;
  storagePath: string | null;
  sha256: string | null;
  mimeType: string | null;
  sizeBytes: number;
  parseStatus: string;
  mergeStatus?: string;
  splitStatus?: string;
  embedStatus?: string;
  createdAt: string;
  updatedAt: string;
};

export type KnowledgeUploadResult = {
  document: KnowledgeDocument;
  duplicate: boolean;
};

export type ProjectKnowledgePayload = {
  documents: KnowledgeDocument[];
  enabledIds: number[];
};

export type KnowledgePendingRef = {
  documentId: number;
  refId: string;
};

export type KnowledgeSearchHit = {
  kind: "text" | "figure" | "table" | string;
  body: string;
  documentId: number;
  documentName: string;
  familyCode: string;
  clauseNo: string;
  figureNo: string;
  tableNo: string;
  pageNumbers: number[];
  refId: string;
  mentionedFigureIds: string[];
  mentionedTableIds: string[];
};

export type KnowledgeSearchResult = {
  notice: string | null;
  hits: KnowledgeSearchHit[];
  pendingFigures: KnowledgePendingRef[];
  pendingTables: KnowledgePendingRef[];
};

export type SapModelPreviewJoint = {
  id: string;
  x: number;
  y: number;
  z?: number;
};

export type SapModelPreviewFrame = {
  i: string;
  j: string;
  name?: string;
  kind?: string;
};

export type SapModelPreview = {
  joints?: SapModelPreviewJoint[];
  frames?: SapModelPreviewFrame[];
};

export type ProjectSapModel = {
  id: number;
  projectId: number;
  taskId: number | null;
  seq: number;
  sapVersion: string | null;
  originalName: string;
  sizeBytes: number;
  frameCount: number | null;
  jointCount: number | null;
  note: string | null;
  previewJson: string | null;
  createdAt: string;
};

export type InquiryThread = {
  id: number;
  projectId: number;
  /** 会话主人；列表接口只回自己的 */
  userId?: number | null;
  title: string;
  createdAt: string;
  updatedAt: string;
};

export type InquiryMessage = {
  id: number;
  threadId: number;
  role: "user" | "agent" | "event";
  body: string;
  createdAt: string;
};

export type InquiryThreadDetail = {
  thread: InquiryThread;
  messages: InquiryMessage[];
  proposedCards?: ModelTaskDetail[];
};

export type TaskKind = "drawing_full" | "drawing_supplement" | "modeling" | "analysis";

export type ModelTask = {
  id: number;
  projectId: number;
  title: string;
  kind: string;
  status: "proposed" | "queued" | "running" | "waiting" | "done" | "failed" | "rejected";
  /** 识图待确认提案；有值且 waiting 时需确认后才写账本 */
  proposalJson?: string | null;
  fileId?: number | null;
  pageKindsJson?: string | null;
  unitSeq?: number | null;
  supportCode?: string | null;
  directive?: string | null;
  proposeReason?: string | null;
  inquiryThreadId?: number | null;
  parentTaskId?: number | null;
  /** draft / inquiry / drawing_button / auto_supplement */
  origin?: string | null;
  createdByUserId?: number | null;
  createdByUsername?: string | null;
  agreedByUserId?: number | null;
  agreedByUsername?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ModelTaskEvent = {
  id: number;
  taskId: number;
  actorUserId?: number | null;
  actorUsername?: string | null;
  body: string;
  createdAt: string;
};

export type ModelTaskDetail = {
  task: ModelTask;
  events: ModelTaskEvent[];
};
