<template>
  <div class="data-monitor-container">
    <div class="background-animator"></div>
    <div></div>
    <el-button class="goto-btn" @click="openInNewWindow">
      {{ $t("pages.dataMonitor.gotoBtn") }}
    </el-button>
  </div>
</template>

<script lang="ts" setup>
import { onMounted, getCurrentInstance, ref } from "vue";

const proxy = getCurrentInstance()?.proxy as any;
const grafanaUrl = ref("");

const openInNewWindow = () => {
  if (!grafanaUrl.value) {
    getGrafanaUrl();
    return;
  }

  window.open(
    // `${grafanaUrl.value}/dashboards`,
    `${grafanaUrl.value}/d/bea7e035-f9d8-4888-a9f7-c72d220c51c5/es-log?orgId=1&from=now-5m&to=now&timezone=browser`, // uid from docker/grafana/dashboards/dashboard.json
    "_blank",
    "noopener,noreferrer",
  );
};

const getGrafanaUrl = async () => {
  const result = await proxy.Request({
    url: proxy.Api.getGfUrl,
  });

  if (!result) return;

  grafanaUrl.value = result.data;
};

onMounted(() => getGrafanaUrl());
</script>
<style lang="scss" scoped>
@keyframes backgroundZoom {
  0% {
    transform: scale(1);
  }

  50% {
    transform: scale(1.1);
  }

  100% {
    transform: scale(1);
  }
}

.data-monitor-container {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-around;
  align-items: center;
  overflow: hidden;

  .background-animator {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: url("../../assets/imgs/grafana/grafana-bg.png");
    background-size: cover;
    animation: backgroundZoom 20s ease-in-out infinite;
  }

  .goto-btn {
    font-size: 18px;
    padding: 20px 25px;
    border-radius: 15px;
    background-color: var(--primary-color);
    color: var(--text-reverse-color);
    border: none;
    z-index: 1;

    &:hover {
      background-color: var(--secondary-color);
      color: var(--text-primary-color);
    }
  }
}
</style>
