<template>
  <el-config-provider :locale="locale">
    <router-view v-slot="{ Component }">
      <keep-alive>
        <component
          :is="Component"
          :key="$route.name"
          v-if="$route.meta.keepAlive"
        />
      </keep-alive>
      <component
        :is="Component"
        :key="$route.name"
        v-if="!$route.meta.keepAlive"
      />
    </router-view>
  </el-config-provider>
</template>

<script setup>
import { ref, watch } from "vue";
import { ElConfigProvider } from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import en from "element-plus/es/locale/lang/en";
import { useStatesStore } from "./stores/statesStore";

const statesStore = useStatesStore();
const locale = ref(en);

watch(() => statesStore.lang, (new_lang) => {
  if (new_lang === "zh") {
    locale.value = zhCn;
  } else {
    locale.value = en;
  }
});
</script>
<style style lang="scss" scoped></style>
