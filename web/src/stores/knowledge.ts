import type { KnowledgeDocument } from "@/api/types";

/** 知识库 / 知识范围共用的三大类。 */
export const knowledgeGroups: { key: KnowledgeDocument["category"]; title: string; hint: string }[] = [
  { key: "code", title: "标准规范", hint: "国家规范与地方规范" },
  { key: "manual", title: "建模指导手册", hint: "SAP2000 与建模流程" },
  { key: "case", title: "工程案例", hint: "内部经验，项目中默认不启用" },
];

export type KnowledgeFamily = {
  key: string;
  latest: KnowledgeDocument;
  versions: KnowledgeDocument[];
};

/**
 * 同一 familyCode 收成一组，主标签取生效日最晚的那本。
 * 没填规范号的各自独立，不出现年版下拉。
 */
export function groupKnowledgeFamilies(docs: KnowledgeDocument[]): KnowledgeFamily[] {
  const buckets = new Map<string, KnowledgeDocument[]>();
  for (const doc of docs) {
    const key = doc.familyCode?.trim() ? `f:${doc.familyCode.trim()}` : `id:${doc.id}`;
    const list = buckets.get(key) ?? [];
    list.push(doc);
    buckets.set(key, list);
  }
  return [...buckets.entries()].map(([key, versions]) => {
    const sorted = [...versions].sort((a, b) => {
      const from = (b.effectiveFrom ?? "").localeCompare(a.effectiveFrom ?? "");
      return from !== 0 ? from : b.id - a.id;
    });
    return { key, latest: sorted[0], versions: sorted };
  });
}

export function formatKnowledgeDate(value?: string | null) {
  if (!value) return "—";
  return value.slice(0, 10);
}
