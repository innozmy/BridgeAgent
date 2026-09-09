<script setup lang="ts">
import { reactive, ref } from "vue";
import { message } from "ant-design-vue";
import { createProject } from "@/api/project";
import { refreshProjectList } from "@/stores/projects";

const open = defineModel<boolean>("open", { default: false });
const emit = defineEmits<{
  created: [id: number];
}>();

const submitting = ref(false);
const form = reactive({
  name: "",
  carriageway: "left",
  code: "",
  region: "",
  openedOn: undefined as string | undefined,
  codeStrategy: undefined as string | undefined,
  intro: "",
});

function reset() {
  form.name = "";
  form.carriageway = "left";
  form.code = "";
  form.region = "";
  form.openedOn = undefined;
  form.codeStrategy = undefined;
  form.intro = "";
}

async function submit() {
  if (!form.name.trim()) {
    message.warning("请填写项目名称");
    return;
  }
  submitting.value = true;
  try {
    const created = await createProject({
      name: form.name.trim(),
      carriageway: form.carriageway,
      code: form.code.trim() || undefined,
      region: form.region.trim() || undefined,
      openedOn: form.openedOn,
      codeStrategy: form.codeStrategy,
      intro: form.intro.trim() || undefined,
    });
    await refreshProjectList();
    message.success("项目已创建");
    open.value = false;
    reset();
    emit("created", created.id);
  } catch (error) {
    message.error(error instanceof Error ? error.message : "创建失败，请确认后端已启动");
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <a-modal
    v-model:open="open"
    title="新建项目"
    ok-text="创建"
    cancel-text="取消"
    :confirm-loading="submitting"
    destroy-on-close
    @ok="submit"
  >
    <a-form layout="vertical">
      <a-form-item label="项目名称" required>
        <a-input v-model:value="form.name" placeholder="例如：沪闵高架连续梁" />
      </a-form-item>
      <a-form-item label="幅面" required>
        <a-select v-model:value="form.carriageway">
          <a-select-option value="left">左幅</a-select-option>
          <a-select-option value="right">右幅</a-select-option>
          <a-select-option value="undivided">不分幅</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="项目标号">
        <a-input v-model:value="form.code" placeholder="可后补，允许重复" />
      </a-form-item>
      <a-form-item label="地区">
        <a-input v-model:value="form.region" placeholder="例如：上海市" />
      </a-form-item>
      <a-form-item label="桥梁落地时间">
        <a-date-picker v-model:value="form.openedOn" value-format="YYYY-MM-DD" style="width: 100%" />
      </a-form-item>
      <a-form-item label="规范策略">
        <a-select v-model:value="form.codeStrategy" allow-clear placeholder="可后补">
          <a-select-option value="at_opening">按落地时点</a-select-option>
          <a-select-option value="current_review">按现行复核</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="简介">
        <a-textarea v-model:value="form.intro" :rows="3" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>
