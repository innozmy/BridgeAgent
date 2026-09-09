import { createApp } from "vue";
import Antd from "ant-design-vue";
import App from "./App.vue";
import router from "./router";
import "ant-design-vue/dist/reset.css";
import "./styles/global.css";

createApp(App).use(router).use(Antd).mount("#app");
