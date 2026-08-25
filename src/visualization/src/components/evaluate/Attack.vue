<template>
  <div class="page-container">
    <el-card class="attack-section">
      <h2 class="section-title">{{ $t("pages.attack.launchAttack") }}</h2>
      <el-form :model="attackForm">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$t('pages.attack.method')" required>
              <el-select
                ref="selectRef"
                v-model="attackForm.method"
                :placeholder="$t('pages.attack.selectMethod')"
                @change="handleMethodChange"
              >
                <el-option-group
                  v-for="(methods, key) in attackMethodGroups"
                  :key="key"
                  :label="key"
                >
                  <el-tooltip
                    v-for="method in methods"
                    :key="method.type"
                    :content="method.description"
                    placement="top"
                  >
                    <el-option :label="method.type" :value="method.type" />
                  </el-tooltip>
                </el-option-group>
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="6" v-if="showAttackerSelect">
            <el-form-item :label="$t('pages.attack.attacker')" required>
              <el-select
                v-model="attackForm.attacker"
                :placeholder="$t('pages.attack.selectAttacker')"
                @change="handleAttackerChange"
              >
                <el-option
                  v-for="node in allowedAttackers"
                  :key="node.nodeId"
                  :label="node.name"
                  :value="node.nodeId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="showAttackerSelect ? 7 : 13">
            <el-form-item :label="$t('pages.attack.target')" required>
              <el-select
                v-model="attackForm.targetIds"
                :placeholder="$t('pages.attack.selectTarget')"
                multiple
                @change="handleTargetsChange"
              >
                <el-option
                  v-for="target in formattedTargets"
                  :key="target.id"
                  :label="target.label"
                  :value="target.id"
                  :class="{ 'recommended-target': target.recommendedRank }"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="1" class="recommend-button-col">
            <el-tooltip content="Get recommended targets" placement="top">
              <el-button
                circle
                :disabled="!canRecommend"
                :class="{ 'recommend-active': canRecommend }"
                @click="fetchRecommendations"
              >
                <el-icon>
                  <Opportunity />
                </el-icon>
              </el-button>
            </el-tooltip>
          </el-col>
        </el-row>

        <el-row>
          <el-col :span="24" class="form-actions">
            <el-tag type="primary" v-if="currentAttackEvent">
              {{ $t("pages.attack.attacking") }}:
              {{ currentAttackEvent?.eventId }} ({{
                $t("pages.attack.duration")
              }}
              {{ attackDuration }})
            </el-tag>
            <el-button type="primary" @click="executeAttack">
              {{ $t("pages.attack.execute") }}
            </el-button>
            <el-button
              type="danger"
              @click="revokeAttack"
              :disabled="!currentAttackEvent"
            >
              {{ $t("pages.attack.revoke") }}
            </el-button>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card class="attack-records-section" style="max-width: 90%">
      <h2 class="section-title">{{ $t("pages.attack.historyRecords") }}</h2>
      <el-table :data="attackRecords" border stripe>
        <el-table-column prop="eventId" label="Event ID" width="180" />
        <el-table-column prop="networkId" label="Network ID" width="100" />
        <el-table-column prop="attackType" label="Attack Type" width="110" />
        <el-table-column prop="attackers" label="Attackers" width="110">
          <template #default="scope">
            {{
              !scope.row.attackers || scope.row.attackers.length === 0
                ? "None"
                : scope.row.attackers.join(", ")
            }}
          </template>
        </el-table-column>
        <el-table-column prop="victims" label="Victims" width="120">
          <template #default="scope">
            {{
              !scope.row.victims || scope.row.victims.length === 0
                ? "None"
                : scope.row.victims.join(", ")
            }}
          </template>
        </el-table-column>
        <el-table-column prop="timestamp" label="Start Time" width="160">
          <template #default="scope">
            {{ formatTimestamp(scope.row.timestamp) }}
          </template>
        </el-table-column>
        <el-table-column prop="endTimestamp" label="End Time" width="160">
          <template #default="scope">
            {{ formatTimestamp(scope.row.endTimestamp) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="attackProcess"
          label="Attack Process"
          min-width="300"
        />
      </el-table>
      <div class="pagination-container">
        <el-pagination
          v-model="pageQuery"
          :page-sizes="[10, 20, 50, 100]"
          :total="totalRecords"
          layout="total, sizes, ->, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import {
  ref,
  reactive,
  computed,
  onMounted,
  onBeforeUnmount,
  getCurrentInstance,
} from "vue";
import { ElMessage } from "element-plus";
import { Opportunity } from "@element-plus/icons-vue";
import type {
  RunningAttackSummary,
  AttackParam,
  AttackLog,
  AttackType,
  Recommended,
} from "@/ifaces/attack";
import type { PageQuery, PageVO } from "@/ifaces/common";

const proxy = getCurrentInstance()?.proxy as any;

const attackMethodGroups = ref<Record<string, AttackType[]>>({});

const attackForm = reactive({
  method: "",
  attacker: null as number | null,
  targetIds: [] as Array<number | string>,
});

const attackParams = ref<AttackParam | null>(null);
const currentAttackEvent = ref<RunningAttackSummary | null>(null);
const attackDuration = ref("00:00:00");
let durationTimer: number | null = null;

const pageQuery = reactive<PageQuery>({
  currentPage: 1,
  pageSize: 10,
});
const totalRecords = ref(0);
const attackRecords = ref<AttackLog[]>([]);
const recommendedTargets = ref<Recommended[]>([]);

const selectedAttack = computed(() => {
  if (!attackForm.method) return null;

  for (let key in attackMethodGroups.value) {
    const methods = attackMethodGroups.value[key];
    const method = methods?.find((m) => m.type === attackForm.method);
    if (method)
      return {
        type: key,
        serialNumber: method.serialNumber,
      };
  }
  return null;
});

const showAttackerSelect = computed(
  () => selectedAttack.value?.type === "BGP-like",
);

const allowedAttackers = computed(() => {
  if (selectedAttack.value?.type === "BGP-like")
    return attackParams.value?.topologySummary.nodes.filter(
      (node) => node.type == "AS",
    );
  return [];
});

const allowedTargets = computed(() => {
  if (selectedAttack.value?.type === "Link-like")
    return attackParams.value?.topologySummary.links;
  else if (selectedAttack.value?.type === "BGP-like") {
    const allowedNodes = attackParams.value?.topologySummary.nodes.filter(
      (node) => node.type == "AS",
    );
    // Exclude attacker if specified
    if (attackForm.attacker)
      return allowedNodes?.filter(
        (node) => node.nodeId !== attackForm.attacker,
      );
    return allowedNodes;
  } else return attackParams.value?.topologySummary.nodes;
});

const formattedTargets = computed(() => {
  const recommendedMap = new Map<number | string, number>();
  recommendedTargets.value.forEach((rec, index) => {
    if (rec.nodeId) {
      recommendedMap.set(rec.nodeId, index + 1);
    }
    if (rec.linkId) {
      recommendedMap.set(rec.linkId, index + 1);
    }
  });

  return allowedTargets.value?.map((target) => {
    const id = "nodeId" in target ? target.nodeId : target.linkId;
    const rank = recommendedMap.get(id);
    if ("nodeId" in target)
      return {
        id: target.nodeId,
        label: rank ? `${target.name} (#${rank})` : target.name,
        recommendedRank: rank || undefined,
      };
    return {
      id: target.linkId,
      label: rank
        ? `Link${target.linkId} (Node${target.srcNodeId} -> Node${target.dstNodeId}) (#${rank})`
        : `Link${target.linkId} (Node${target.srcNodeId} -> Node${target.dstNodeId})`,
      recommendedRank: rank || undefined,
    };
  });
});

const canExecuteAttack = computed(() => {
  if (!attackForm.method) return false;
  if (selectedAttack.value?.type === "BGP-like" && !attackForm.attacker)
    return false;
  if (!attackForm.targetIds || attackForm.targetIds.length === 0) return false;
  if (currentAttackEvent.value) return false;
  return true;
});

const canRecommend = computed(() => {
  if (!attackForm.method || !selectedAttack.value) return false;
  if (selectedAttack.value?.type === "BGP-like" && !attackForm.attacker)
    return false;
  return true;
});

const handleMethodChange = () => {
  attackForm.attacker = null;
  attackForm.targetIds = [];
  recommendedTargets.value = [];
};

const handleAttackerChange = () =>
  (attackForm.targetIds = attackForm.targetIds.filter(
    (target) => target !== attackForm.attacker,
  ));

const handleTargetsChange = () => {
  if (attackForm.attacker) {
    attackForm.targetIds = attackForm.targetIds.filter(
      (target) => target !== attackForm.attacker,
    );
  }
};

const fetchRecommendations = async () => {
  if (!canRecommend.value) return;

  const attackInfo = selectedAttack.value;
  if (!attackInfo) return;

  let attackCategory: number;
  let attackerId: number | undefined;

  if (attackInfo.type === "BGP-like") {
    if (!attackForm.attacker) return;
    attackCategory = 0;
    attackerId = attackForm.attacker;
  } else if (attackInfo.type === "Node-like") {
    attackCategory = 1;
  } else if (attackInfo.type === "Link-like") {
    attackCategory = 2;
  } else return;

  const result = await proxy.Request({
    url: proxy.Api.recommendTargets,
    requestType: proxy.Request.requestTypeForm,
    data: {
      attackCategory,
      attackerId,
    },
  });

  if (!result) return;

  recommendedTargets.value = result.data || [];
};

const startDurationTimer = (startTime: number) => {
  stopDurationTimer();

  const updateDuration = () => {
    const now = Date.now();
    const diff = now - startTime;

    const hours = Math.floor(diff / (1000 * 60 * 60));
    const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
    const seconds = Math.floor((diff % (1000 * 60)) / 1000);

    attackDuration.value = `${hours.toString().padStart(2, "0")}:${minutes.toString().padStart(2, "0")}:${seconds.toString().padStart(2, "0")}`;
  };

  updateDuration();
  durationTimer = window.setInterval(updateDuration, 1000);
};

const stopDurationTimer = () => {
  if (durationTimer) {
    clearInterval(durationTimer);
    durationTimer = null;
    attackDuration.value = "00:00:00";
  }
};

const handleSizeChange = (size: number) => {
  pageQuery.pageSize = size;
  pageQuery.currentPage = 1;

  loadAttackRecords();
};

const handleCurrentChange = (current: number) => {
  pageQuery.currentPage = current;

  loadAttackRecords();
};

const formatTimestamp = (timestamp: number): string => {
  if (!timestamp) return "";
  const date = new Date(timestamp * 1000);

  return date.toLocaleString();
};

const loadAttackableInfo = async () => {
  const result = await proxy.Request({
    url: proxy.Api.loadAttackableInfo,
    data: {},
  });
  if (!result) return;

  attackParams.value = result.data;
  currentAttackEvent.value = result.data.runningAttackSummary;

  // update attackMethodGroups
  if (attackParams.value?.supportAttackTypes) {
    const groups: Record<string, AttackType[]> = {};
    // group by belongTo
    attackParams.value.supportAttackTypes.forEach((attackType) => {
      const belongTo = attackType.belongTo;
      if (!groups[belongTo]) {
        groups[belongTo] = [];
      }
      groups[belongTo].push({ ...attackType });
    });
    attackMethodGroups.value = groups;
  } else {
    attackMethodGroups.value = {};
  }

  // update names
  if (attackParams.value?.topologySummary) {
    attackParams.value.topologySummary.nodes.forEach((node) => {
      node.name += ` (${node.type})`;
    });
  }

  if (currentAttackEvent.value) {
    startDurationTimer(currentAttackEvent.value.startTime * 1000);
  }
};

const executeAttack = async () => {
  if (!canExecuteAttack.value) {
    ElMessage.warning(
      "Please complete the attack parameters or stop the current attack",
    );

    return;
  }

  let apiUrl = "";
  let requestData: Record<string, any> = {
    targetIds: attackForm.targetIds,
  };

  const attackInfo = selectedAttack.value;
  if (!attackInfo) return;

  if (attackInfo.type === "BGP-like") {
    apiUrl = `/attack/bgp/${attackInfo.serialNumber}`;
    requestData.attackerId = attackForm.attacker;
  } else if (attackInfo.type === "Node-like") {
    apiUrl = `/attack/node/${attackInfo.serialNumber}`;
  } else if (attackInfo.type === "Link-like") {
    apiUrl = `/attack/link/${attackInfo.serialNumber}`;
  }

  const result = await proxy.Request({
    url: apiUrl,
    data: requestData,
  });

  if (!result || !result.data) return;

  const attackEventId = result.data;
  ElMessage.success(result.mes);

  currentAttackEvent.value = {
    eventId: attackEventId,
    startTime: Date.now(),
  };

  startDurationTimer(currentAttackEvent.value.startTime);

  setTimeout(() => {
    loadAttackRecords();
  }, 500);
};

const revokeAttack = async () => {
  if (!currentAttackEvent.value) return;

  const result = await proxy.Request({
    url: proxy.Api.revokeAttack,
    requestType: proxy.Request.requestTypeForm,
    data: {
      attackId: currentAttackEvent.value.eventId,
    },
  });

  if (!result) return;

  ElMessage.success(result.mes);
  currentAttackEvent.value = null;

  stopDurationTimer();

  setTimeout(() => {
    loadAttackRecords();
  }, 500);
};

const loadAttackRecords = async () => {
  const result = await proxy.Request({
    url: "/attack/loadAttackRecords",
    data: pageQuery,
  });

  if (!result) return;

  const pageVO: PageVO<AttackLog> = result.data;
  totalRecords.value = pageVO.total;
  attackRecords.value = pageVO.records;
};

onBeforeUnmount(() => stopDurationTimer());

onMounted(() => {
  loadAttackableInfo();
  loadAttackRecords();
});
</script>

<style lang="scss" scoped>
.page-container {
  padding: 15px;
  background-color: var(--bg-color);
  width: 100%;
  min-height: 100%;

  .attack-section,
  .attack-records-section {
    background-color: var(--card-bg-color);
    border-radius: 12px;
    padding: 10px;
    box-shadow: var(--box-shadow);
    border: 1px solid var(--border-color);
    margin: 40px auto;
    max-width: 1000px;

    .section-title {
      font-size: 22px;
      font-weight: 600;
      color: var(--primary-color);
      margin-bottom: 15px;
    }
  }

  .attack-section {
    .recommend-button-col {
      .el-button {
        width: 32px;
        height: 32px;
        padding: 0;
        background-color: var(--bg-color);
        border: 1px solid var(--border-color);
        color: var(--text-secondary-color);

        .el-icon {
          font-size: 20px;
        }

        &:disabled {
          cursor: not-allowed;
        }

        &.recommend-active {
          color: #e6a23c;
          border-color: #f5dab1;

          &:hover {
            color: #eb8722;
            border-color: #d6af74;
          }
        }
      }
    }

    :deep(.recommended-target) {
      color: #e6a23c;
    }

    .form-actions {
      text-align: right;
      margin-top: 20px;

      .el-tag {
        font-size: 16px;
        padding: 15px 15px;
        border-radius: 10px;
        margin-right: 20px;
      }

      .el-button {
        font-size: 16px;
        padding: 20px 15px;
        border-radius: 8px;
        border: none;
      }

      .el-button--primary {
        background-color: var(--primary-color);
        color: var(--text-reverse-color);

        &:hover,
        &:focus {
          background-color: var(--secondary-color);
          color: var(--text-primary-color);
        }
      }
    }
  }

  .attack-records-section {
    :deep(.el-table) {
      margin-bottom: 20px;
      background-color: var(--card-bg-color);
      border: 1px solid var(--border-color);

      .el-table__header-wrapper {
        th {
          background-color: var(--hover-bg-color);
          color: var(--text-secondary-color);
          border-bottom: 1px solid var(--border-color);
        }
      }

      .el-table__body-wrapper {
        tr {
          td {
            border-bottom: 1px solid var(--border-color);
            color: var(--text-secondary-color);
          }
        }
      }
    }

    .pagination-container {
      :deep(.el-pagination) {
        .el-pagination__total {
          color: var(--text-primary-color);
          font-weight: 700;
        }

        .btn-prev,
        .btn-next {
          &:hover {
            background-color: var(--hover-bg-color);
          }
        }

        .el-pager li {
          background-color: var(--button-bg-color);
          color: var(--text-primary-color);

          &:hover {
            background-color: var(--secondary-color);
          }
        }
      }
    }
  }
}
</style>
