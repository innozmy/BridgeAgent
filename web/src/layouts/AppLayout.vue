<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { message } from "ant-design-vue";
import AppLogo from "@/components/AppLogo.vue";
import CreateProjectModal from "@/components/CreateProjectModal.vue";
import { fetchMe, toSessionProfile, updateProfile, changePassword, uploadAvatar } from "@/api/auth";
import { projectList, refreshProjectList } from "@/stores/projects";
import {
  applyProfile,
  canManageProjects,
  canOpenSystem,
  canManageUsers,
  clearSession,
  currentProfile,
  getToken,
} from "@/stores/session";

const route = useRoute();
const router = useRouter();
const createOpen = ref(false);

const nav = [
  {
    key: "workbench",
    label: "工作台",
    match: (p: string) => p.startsWith("/workbench") || p.startsWith("/projects") || p === "/",
  },
  {
    key: "knowledge",
    label: "知识库",
    match: (p: string) => p.startsWith("/knowledge"),
  },
  {
    key: "queue",
    label: "资源队列",
    match: (p: string) => p.startsWith("/queue"),
  },
];

const systemNav = {
  key: "system",
  label: "系统管理",
  match: (p: string) => p.startsWith("/system") || p.startsWith("/admin"),
};

const activeKey = computed(() => {
  const items = canOpenSystem.value ? [...nav, systemNav] : nav;
  const hit = items.find((item) => item.match(route.path));
  return hit?.key ?? "";
});

const currentProjectId = computed(() => String(route.params.id ?? ""));
const displayName = computed(() => currentProfile.value?.nickname || currentProfile.value?.username || "未登录");
const displayMeta = computed(() => currentProfile.value?.username ?? "");
const avatarChar = computed(() => displayName.value.slice(0, 1));

const profileOpen = ref(false);
const profileForm = reactive({ nickname: "", oldPassword: "", newPassword: "" });

async function saveProfile() {
  try {
    if (profileForm.nickname.trim()) {
      const me = await updateProfile(profileForm.nickname.trim());
      applyProfile(toSessionProfile(me));
    }
    if (profileForm.oldPassword && profileForm.newPassword) {
      await changePassword(profileForm.oldPassword, profileForm.newPassword);
      message.success("密码已改，请重新登录");
      logout();
      return;
    }
    profileOpen.value = false;
    message.success("已保存");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "保存失败");
  }
}

async function onAvatar(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) {
    return;
  }
  try {
    const me = await uploadAvatar(file);
    applyProfile(toSessionProfile(me));
    message.success("头像已更新");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "上传失败");
  }
  input.value = "";
}

function logout() {
  clearSession();
  router.push("/login");
}

function openProfile() {
  profileForm.nickname = currentProfile.value?.nickname ?? "";
  profileForm.oldPassword = "";
  profileForm.newPassword = "";
  profileOpen.value = true;
}

function goNav(key: string) {
  if (key === "system") {
    router.push(canManageUsers.value ? "/system/users" : "/system/project-members");
    return;
  }
  router.push(`/${key}`);
}

function openProject(id: number | string) {
  router.push(`/projects/${id}/overview`);
}

const visibleNav = computed(() => (canOpenSystem.value ? [...nav, systemNav] : nav));

onMounted(async () => {
  try {
    const me = await fetchMe();
    const token = getToken();
    if (token) {
      applyProfile(toSessionProfile(me));
    }
  } catch {
    // 401 由 http 层跳登录；其它错误不挡项目列表
  }
  try {
    await refreshProjectList();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "无法加载项目，请确认后端已启动");
  }
});
</script>

<template>
  <div class="shell">
    <aside class="sider">
      <div class="brand" @click="router.push('/workbench')">
        <AppLogo :size="32" />
        <div class="brand-text">
          <div class="brand-name">BridgeAgent</div>
          <div class="brand-sub">桥梁有限元</div>
        </div>
      </div>

      <button v-if="canManageProjects" class="new-btn" type="button" @click="createOpen = true">
        <span class="plus">+</span>
        新建项目
      </button>

      <nav class="nav">
        <button
          v-for="item in visibleNav"
          :key="item.key"
          type="button"
          class="nav-item"
          :class="{ on: item.key === activeKey }"
          @click="goNav(item.key)"
        >
          <svg v-if="item.key === 'workbench'" class="ico" viewBox="0 0 16 16" aria-hidden="true">
            <path fill="currentColor" d="M1.5 2.5h6v6h-6v-6zm7 0h6v4h-6v-4zm0 5h6v6h-6v-6zm-7 3h6v3h-6v-3z" />
          </svg>
          <svg v-else-if="item.key === 'knowledge'" class="ico" viewBox="0 0 16 16" aria-hidden="true">
            <path
              fill="currentColor"
              d="M3 2.5h4.2c.8 0 1.5.3 2 .8.5-.5 1.2-.8 2-.8H15v11h-3.8c-.7 0-1.4.2-2 .6-.6-.4-1.3-.6-2-.6H3v-11zm1.2 1.2v8.6h2.6c.7 0 1.4.2 2 .5V4.5c-.6-.3-1.2-.5-1.9-.5H4.2z"
            />
          </svg>
          <svg v-else-if="item.key === 'queue'" class="ico" viewBox="0 0 16 16" aria-hidden="true">
            <path fill="currentColor" d="M2 3.2h12v1.5H2V3.2zm0 4h12v1.5H2V7.2zm0 4h8.5V12.7H2v-1.5z" />
          </svg>
          <svg v-else class="ico" viewBox="0 0 16 16" aria-hidden="true">
            <path
              fill="currentColor"
              d="M8 8a2.6 2.6 0 1 0 0-5.2A2.6 2.6 0 0 0 8 8zm-5.2 6v-.8c0-2 3.5-3.1 5.2-3.1s5.2 1.1 5.2 3.1v.8H2.8z"
            />
          </svg>
          {{ item.label }}
        </button>
      </nav>

      <div class="recent-label">最近项目</div>
      <div class="recent">
        <button
          v-for="item in projectList"
          :key="item.id"
          type="button"
          class="recent-item"
          :class="{ on: currentProjectId === String(item.id) }"
          @click="openProject(item.id)"
        >
          {{ item.name }}
        </button>
      </div>

      <div class="foot">
        <div class="avatar">{{ avatarChar }}</div>
        <div class="user-box">
          <div class="user-name">{{ displayName }}</div>
          <div class="user-meta">{{ displayMeta }}</div>
        </div>
        <button class="logout" type="button" @click="openProfile">资料</button>
        <button class="logout" type="button" @click="logout">退出</button>
      </div>
    </aside>
    <main class="main">
      <router-view />
    </main>
    <CreateProjectModal v-model:open="createOpen" @created="openProject" />
    <a-modal v-model:open="profileOpen" title="个人资料" ok-text="保存" @ok="saveProfile">
      <a-form layout="vertical">
        <a-form-item label="头像">
          <input type="file" accept="image/png,image/jpeg,image/webp" @change="onAvatar" />
        </a-form-item>
        <a-form-item label="昵称">
          <a-input v-model:value="profileForm.nickname" />
        </a-form-item>
        <a-form-item label="原密码（改密才填）">
          <a-input-password v-model:value="profileForm.oldPassword" />
        </a-form-item>
        <a-form-item label="新密码">
          <a-input-password v-model:value="profileForm.newPassword" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  min-height: 100vh;
  background: #fff;
}
.sider {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  padding: 14px 12px 12px;
  background: #f7f8fc;
  border-right: 1px solid #eceef5;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 8px 14px;
  cursor: pointer;
}
.brand-name {
  font-weight: 650;
  font-size: 15px;
  letter-spacing: 0.01em;
}
.brand-sub {
  margin-top: 1px;
  color: #8b90a0;
  font-size: 12px;
}
.new-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 36px;
  margin-bottom: 12px;
  border: 1px solid #c9d2ff;
  border-radius: 10px;
  background: #fff;
  color: #4d6bfe;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.new-btn:hover {
  background: #eef2ff;
}
.plus {
  font-size: 16px;
  line-height: 1;
}
.nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  height: 38px;
  padding: 0 10px;
  border: 0;
  border-radius: 10px;
  background: none;
  color: #2b2f3a;
  font: inherit;
  font-size: 14px;
  text-align: left;
  cursor: pointer;
}
.nav-item:hover {
  background: #eef0f7;
}
.nav-item.on {
  background: #e8edff;
  color: #3b5bfd;
  font-weight: 600;
}
.ico {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}
.recent-label {
  margin: 16px 8px 6px;
  color: #8b90a0;
  font-size: 12px;
  font-weight: 600;
}
.recent {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.recent-item {
  display: block;
  width: 100%;
  padding: 7px 10px;
  border: 0;
  border-radius: 8px;
  background: none;
  color: #4b5060;
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.recent-item:hover {
  background: #eef0f7;
}
.recent-item.on {
  background: #fff;
  color: #3b5bfd;
}
.foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  padding: 10px 8px 4px;
  border-top: 1px solid #eceef5;
}
.user-box {
  min-width: 0;
  flex: 1;
}
.logout {
  flex-shrink: 0;
  border: 0;
  background: none;
  color: #8b90a0;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.logout:hover {
  color: #3b5bfd;
}
.avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #4d6bfe;
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 600;
}
.user-name {
  font-size: 13px;
  font-weight: 600;
}
.user-meta {
  font-size: 12px;
  color: #8b90a0;
}
.main {
  flex: 1;
  min-width: 0;
  padding: 22px 28px 36px;
}
</style>
