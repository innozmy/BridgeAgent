import { createRouter, createWebHistory } from "vue-router";
import AppLayout from "@/layouts/AppLayout.vue";
import ProjectLayout from "@/layouts/ProjectLayout.vue";
import { canManageUsers, canOpenSystem, getToken } from "@/stores/session";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/login",
      name: "login",
      component: () => import("@/pages/Login.vue"),
    },
    {
      path: "/",
      component: AppLayout,
      redirect: "/workbench",
      children: [
        {
          path: "workbench",
          name: "workbench",
          component: () => import("@/pages/Workbench.vue"),
        },
        {
          path: "knowledge",
          name: "knowledge",
          component: () => import("@/pages/Knowledge.vue"),
        },
        {
          path: "queue",
          name: "queue",
          component: () => import("@/pages/Queue.vue"),
        },
        {
          path: "system",
          component: () => import("@/layouts/SystemLayout.vue"),
          redirect: () => (canManageUsers.value ? "/system/users" : "/system/project-members"),
          children: [
            {
              path: "users",
              name: "system-users",
              meta: { needSuper: true },
              component: () => import("@/pages/system/Users.vue"),
            },
            {
              path: "roles",
              name: "system-roles",
              meta: { needSuper: true },
              component: () => import("@/pages/system/Roles.vue"),
            },
            {
              path: "audit",
              name: "system-audit",
              meta: { needSuper: true },
              component: () => import("@/pages/system/Audit.vue"),
            },
            {
              path: "project-members",
              name: "system-project-members",
              meta: { needProjectAdmin: true },
              component: () => import("@/pages/system/ProjectMembers.vue"),
            },
          ],
        },
        {
          path: "admin",
          redirect: "/system/users",
        },
        {
          path: "projects/:id",
          component: ProjectLayout,
          redirect: (to) => `/projects/${to.params.id}/overview`,
          children: [
            { path: "overview", name: "overview", component: () => import("@/pages/project/Overview.vue") },
            { path: "drawings", name: "drawings", component: () => import("@/pages/project/Drawings.vue") },
            { path: "inquiry", name: "inquiry", component: () => import("@/pages/project/Inquiry.vue") },
            { path: "tasks", name: "tasks", component: () => import("@/pages/project/Tasks.vue") },
            { path: "models", name: "models", component: () => import("@/pages/project/Models.vue") },
            { path: "findings", name: "findings", component: () => import("@/pages/project/Findings.vue") },
            { path: "scope", name: "scope", component: () => import("@/pages/project/Scope.vue") },
            { path: "members", name: "members", component: () => import("@/pages/project/Members.vue") },
          ],
        },
      ],
    },
  ],
});

router.beforeEach((to) => {
  const token = getToken();
  if (to.path === "/login") {
    return token ? "/workbench" : true;
  }
  if (!token) {
    return { path: "/login", query: { redirect: to.fullPath } };
  }
  if (to.meta.needSuper && !canManageUsers.value) {
    return "/workbench";
  }
  if (to.meta.needProjectAdmin && !canOpenSystem.value) {
    return "/workbench";
  }
  return true;
});

export default router;
