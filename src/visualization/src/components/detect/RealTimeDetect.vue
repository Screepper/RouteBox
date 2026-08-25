<template>
  <div class="page-container">
    <div class="detect-container">
      <h2 class="detect-title">{{ $t("pages.realTimeDetect.title") }}</h2>

      <div class="status-indicator">
        <el-tag :type="isDetecting ? 'success' : 'warning'" size="large">
          {{
            isDetecting
              ? $t("pages.realTimeDetect.status.detecting")
              : $t("pages.realTimeDetect.status.waiting")
          }}
        </el-tag>
      </div>

      <div class="timeline-container">
        <el-timeline>
          <el-timeline-item v-for="(message, index) in displayMess" :key="index" :timestamp="message.time"
            :type="getMessageType(message.stage)" :icon="getMessageIcon(message.stage)">
            <div class="mes-content">
              <div v-if="message.stage === DetectState.START" class="mes stage-mes start-mes">
                {{ $t("pages.realTimeDetect.messages.start") }}
                <div class="start-desc" v-if="message.mes">
                  {{ message.mes }}
                </div>
              </div>
              <div v-else-if="message.stage === DetectState.PROCESS_DATA" class="mes process-mes">
                {{ message.mes }}
              </div>
              <div v-else-if="message.stage === DetectState.ANOMALY_DETECT" class="mes detect-mes">
                {{ message.mes }}
              </div>
              <div v-else-if="message.stage === DetectState.DETECT_RESULT" class="mes result-mes">
                {{ message.mes }}
              </div>
              <div v-else-if="message.stage === DetectState.STOP" class="mes stage-mes stop-mes">
                {{ $t("pages.realTimeDetect.messages.stop") }}
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>

        <div v-if="displayMess.length === 0" class="empty-state">
          {{ $t("pages.realTimeDetect.messages.empty") }}
        </div>
      </div>
      <div class="mark"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  getCurrentInstance,
  ref,
  onMounted,
  onUnmounted,
  computed,
  nextTick,
  onActivated,
} from "vue";
import DetectState from "@/common/DetectState";
import { useRdMesStore } from "@/stores/rdMesStore";
import type { RdMes } from "@/ifaces/detect";

const proxy = getCurrentInstance()?.proxy as any;

const rdMesStore = useRdMesStore();

// States
const isDetecting = ref(false);
const wsInstance = ref<any>(null);
const isManualDisconnect = ref(false);

const displayMess = computed(() => rdMesStore.getMessages());

const connectWS = () => {
  // Close existing connection if it exists
  if (wsInstance.value) {
    try {
      wsInstance.value.close();
    } catch (error) {
      console.error("Error closing existing WebSocket:", error);
    }
    wsInstance.value = null;
  }

  wsInstance.value = proxy.Ws({
    url: proxy.Api.rd,
    onMessage: (event: MessageEvent) => {
      try {
        const mes: RdMes = JSON.parse(event.data);
        // Waiting...
        if (mes.stage === DetectState.WAIT) {
          isDetecting.value = false;
          return;
        }
        // Detecting...
        if (mes.stage === DetectState.START) {
          isDetecting.value = true;
          rdMesStore.clearMes();
        }
        // Add message to store
        rdMesStore.addMes(mes);
        // Auto scroll to bottom when new message arrives
        scrollToBottom();
      } catch (error) {
        console.error("Error processing WebSocket message:", error);
      }
    },
    onClose: () => {
      console.log("WebSocket Connection Closed");
      if (!isManualDisconnect.value) {
        // Only reconnect if not manually disconnected
        setTimeout(() => connectWS(), 3000);
      }
    },
  });
};

const disconnectWS = () => {
  if (wsInstance.value) {
    isManualDisconnect.value = true;
    wsInstance.value.close();
    wsInstance.value = null;
    isDetecting.value = false;
  }
};

const getMessageType = (stage: number) => {
  switch (stage) {
    case DetectState.START:
      return "success";
    case DetectState.STOP:
      return "info";
    case DetectState.PROCESS_DATA:
      return "primary";
    case DetectState.ANOMALY_DETECT:
      return "warning";
    case DetectState.DETECT_RESULT:
      return "danger";
  }
};

const getMessageIcon = (stage: number) => {
  switch (stage) {
    case DetectState.START:
      return "CircleCheckFilled";
    case DetectState.STOP:
      return "CircleCloseFilled";
    case DetectState.PROCESS_DATA:
      return "InfoFilled";
    default:
      return "WarningFilled";
  }
};

const scrollToBottom = () => {
  nextTick(() => {
    document.querySelector(".mark")?.scrollIntoView({
      behavior: "smooth",
      block: "end",
      inline: "nearest",
    });
  });
};

onMounted(() => {
  connectWS();
  scrollToBottom();
});

onUnmounted(() => {
  disconnectWS();
});

onActivated(() => {
  scrollToBottom();
});
</script>

<style lang="scss" scoped>
.page-container {
  min-height: 100vh;
  background-color: var(--bg-color);

  .detect-container {
    max-width: 1200px;
    margin: 0 auto;
    padding-top: 20px;

    .detect-title {
      font-size: 28px;
      font-weight: 600;
      margin-bottom: 15px;
      color: var(--primary-color);
      text-align: center;
    }

    .status-indicator {
      text-align: center;
      margin-bottom: 20px;
    }

    .timeline-container {
      background-color: var(--card-bg-color);
      border-radius: 12px;
      padding: 20px;
      border: 1px solid var(--border-color);
      box-shadow: var(--box-shadow);

      :deep(.el-timeline-item__timestamp) {
        color: var(--el-text-color-primary);
      }

      .mes-content {
        background-color: var(--bg-color);
        padding: 15px;
        border-radius: 8px;
        border: 1px solid var(--border-color);
        white-space: pre-wrap;
        word-break: break-word;
        box-shadow: inset var(--box-shadow);

        .mes {
          font-weight: 500;
        }

        .stage-mes {
          font-size: 20px;
          text-align: center;
          padding: 5px;
        }

        .start-mes {
          color: var(--el-color-success);

          .start-desc {
            white-space: pre-wrap;
            font-size: 17px;
            color: var(--el-color-info);
          }
        }

        .stop-mes {
          color: var(--el-color-info);
        }

        .process-mes {
          color: var(--el-color-primary);
        }

        .detect-mes {
          color: var(--el-color-warning);
        }

        .result-mes {
          color: var(--el-color-danger);
        }
      }
    }

    .empty-state {
      text-align: center;
      padding: 40px;
      color: var(--text-secondary-color);
      font-style: italic;
    }
  }
}

.card-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;

  .card-title {
    font-weight: 600;
    font-size: 15px;
    color: var(--primary-color);
    padding-bottom: 5px;
    margin-bottom: 10px;
    border-bottom: 1px solid var(--border-color);
  }
}
</style>
