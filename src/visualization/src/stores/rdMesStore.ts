import { defineStore } from "pinia";
import { ref } from "vue";
import type { RdMes } from "@/ifaces/detect";

export const useRdMesStore = defineStore(
  "RdMesStore",
  () => {
    // State
    const mess = ref<RdMes[]>([]);

    // Actions
    function clearMes() {
      mess.value = [];
    }

    function addMes(mes: RdMes) {
      // Check if the last message has the same stage
      const messLen = mess.value.length;
      if (messLen > 0) {
        const lastMes: RdMes = mess.value[messLen - 1] as RdMes;
        if (lastMes.stage === mes.stage && mes.mes) {
          // Merge messages with the same stage
          lastMes.mes += `\n${mes.mes}`;

          return;
        }
      }
      // Add new message
      mess.value.push(mes);
    }

    function getMessages(): RdMes[] {
      return mess.value;
    }

    return {
      mess,
      clearMes,
      addMes,
      getMessages,
    };
  },
  {
    persist: true,
  },
);
