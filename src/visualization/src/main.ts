import { createApp } from "vue";
import App from "./App.vue";

const app = createApp(App);

// Global styles
import "@/assets/styles/base.scss";
import "@/assets/styles/theme.scss";

// Plugins
// style
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import "element-plus/theme-chalk/dark/css-vars.css";
// router
import router from "@/router";
// persistence
import piniaPluginPersistedstate from "pinia-plugin-persistedstate";
import { createPinia } from "pinia";
// internationalization
import i18n from "@/lang/index";

// Components
import Request from "@/utils/Request";
import WS from "@/utils/WS";
import Api from "@/common/Api";
// mount
app.config.globalProperties.Request = Request;
app.config.globalProperties.Ws = WS;
app.config.globalProperties.Api = Api;

app
  .use(ElementPlus)
  .use(router)
  .use(createPinia().use(piniaPluginPersistedstate))
  .use(i18n)
  .mount("#app");
