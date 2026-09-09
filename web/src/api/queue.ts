import { http } from "./http";

export type ResourceQueueItem = {
  lane: string;
  state: "running" | "queued";
  title: string;
  projectId?: number | null;
  projectName?: string | null;
  refType: "task" | "knowledge";
  refId: number;
  step?: string | null;
  actorUsername?: string | null;
  updatedAt: string;
};

export type ResourceQueueLane = {
  id: string;
  name: string;
  core: number;
  running: number;
  queued: number;
  items: ResourceQueueItem[];
};

export type ResourceQueueSnapshot = {
  lanes: ResourceQueueLane[];
};

export function getResourceQueue() {
  return http<ResourceQueueSnapshot>("/api/resource-queue");
}
