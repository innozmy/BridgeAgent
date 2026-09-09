<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import AppLogo from "@/components/AppLogo.vue";
import { login, toSessionProfile } from "@/api/auth";
import { setSession } from "@/stores/session";

const router = useRouter();
const route = useRoute();
const submitting = ref(false);
const errorText = ref("");
const form = reactive({
  username: "",
  password: "",
});

onMounted(() => {
  if (route.query.reason === "kicked") {
    errorText.value = "账号已在其他设备登录，请重新登录";
  }
});

function safeRedirect(raw: unknown): string {
  if (typeof raw !== "string" || !raw.startsWith("/") || raw.startsWith("//") || raw.startsWith("/login")) {
    return "/workbench";
  }
  return raw;
}

async function submit() {
  const username = form.username.trim();
  if (!username || !form.password) {
    errorText.value = "请输入用户名和密码";
    return;
  }
  errorText.value = "";
  submitting.value = true;
  try {
    const auth = await login(username, form.password);
    if (!auth.token) {
      throw new Error("登录响应缺少令牌");
    }
    setSession(auth.token, toSessionProfile(auth));
    await router.replace(safeRedirect(route.query.redirect));
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : "登录失败";
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="login">
    <div class="panel">
      <div class="brand">
        <AppLogo :size="32" />
        BridgeAgent
      </div>
      <p>桥梁设计一所 · 请登录后进入工作台</p>
      <a-form layout="vertical" @submit.prevent="submit">
        <a-form-item label="用户名">
          <a-input
            v-model:value="form.username"
            autocomplete="username"
            placeholder="用户名"
            :disabled="submitting"
          />
        </a-form-item>
        <a-form-item label="密码">
          <a-input-password
            v-model:value="form.password"
            autocomplete="current-password"
            placeholder="密码"
            :disabled="submitting"
          />
        </a-form-item>
        <p v-if="errorText" class="err">{{ errorText }}</p>
        <a-button type="primary" block :loading="submitting" html-type="submit">登录</a-button>
      </a-form>
    </div>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: #f7f8fc;
}
.panel {
  width: 360px;
  padding: 24px;
  background: #fff;
  border: 1px solid #eceef5;
  border-radius: 12px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 650;
  font-size: 16px;
}
p {
  margin: 10px 0 18px;
  color: #8b90a0;
  font-size: 14px;
}
.err {
  margin: 0 0 12px;
  color: #d4380d;
  font-size: 13px;
}
</style>
