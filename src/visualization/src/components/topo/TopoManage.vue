<template>
  <div class="page-container">
    <div class="title-container">
      {{ $t("pages.topoManage.label") }}
    </div>
    <div v-if="topologyInfo" class="topology-info-container">
      <div class="topology-info-card">
        <div class="info-item">
          <span class="info-label">Running Network ID</span>
          <span class="info-value">{{ topologyInfo.runningNetworkId }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">SERNES Frontend URL</span>
          <span class="info-value">
            <a :href="topologyInfo.sernesFrontendUrl" target="_blank">
              {{ topologyInfo.sernesFrontendUrl }}
            </a>
          </span>
        </div>
        <div class="info-item">
          <span class="info-label">Node Count</span>
          <span class="info-value">
            {{ topologyInfo.nodes.length }}
            <el-button
              class="detail-button"
              @click="nodeDialogVisible = true"
              style="margin-left: 10px"
            >
              Detail
            </el-button>
          </span>
        </div>
        <div class="info-item">
          <span class="info-label">Link Count</span>
          <span class="info-value">
            {{ topologyInfo.linkCount }}
            <el-button
              class="detail-button"
              @click="linkDialogVisible = true"
              style="margin-left: 10px"
            >
              Detail
            </el-button>
          </span>
        </div>
        <div class="info-item">
          <span class="info-label">AS Node Count</span>
          <span class="info-value">{{ topologyInfo.asNodeCount }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">Host Node Count</span>
          <span class="info-value">{{ topologyInfo.hostNodeCount }}</span>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="nodeDialogVisible"
      title="Node Details"
      width="80%"
      :before-close="() => (nodeDialogVisible = false)"
    >
      <el-table class="table" :data="topologyInfo?.nodes" border stripe>
        <el-table-column prop="nodeId" label="Node ID" width="100" />
        <el-table-column prop="name" label="Name" width="150" />
        <el-table-column prop="type" label="Type" width="120" />
        <el-table-column prop="status" label="Status" width="100">
          <template #default="scope">
            <el-tag
              class="tag"
              :type="scope.row.status === 'UP' ? 'success' : 'danger'"
              :icon="scope.row.status === 'UP' ? '' : 'WarningFilled'"
            >
              {{ scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="neighborVethInfo"
          label="Veth Pairs"
          min-width="150"
        >
          <template #default="scope">
            <div style="white-space: pre-wrap">
              {{ scope.row.neighborVethInfo.join("\n") }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="pingableIp" label="Pingable IP" width="120" />
      </el-table>
    </el-dialog>

    <el-dialog
      v-model="linkDialogVisible"
      title="Link Details"
      width="50%"
      :before-close="() => (linkDialogVisible = false)"
    >
      <el-table class="table" :data="topologyInfo?.links" border stripe>
        <el-table-column prop="linkId" label="Link ID" />
        <el-table-column prop="srcNodeId" label="Source Node ID" />
        <el-table-column prop="dstNodeId" label="Target Node ID" />
        <el-table-column prop="status" label="Status">
          <template #default="scope">
            <el-tag
              class="tag"
              :type="scope.row.status === 'UP' ? 'success' : 'danger'"
              :icon="scope.row.status === 'UP' ? '' : 'WarningFilled'"
            >
              {{ scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { getCurrentInstance, ref, onMounted } from "vue";
import type { TopologyInfo } from "@/ifaces/topo";

const proxy = getCurrentInstance()?.proxy as any;

const topologyInfo = ref<TopologyInfo | null>(null);
const nodeDialogVisible = ref(false);
const linkDialogVisible = ref(false);

const getTopologyInfo = async () => {
  const result = await proxy.Request({
    url: proxy.Api.getTopology,
    data: {},
  });

  if (!result || !result.data) return;

  topologyInfo.value = result.data;
};

onMounted(() => {
  getTopologyInfo();
});
</script>

<style lang="scss" scoped>
.page-container {
  padding: 15px;
  background-color: var(--bg-color);

  .title-container {
    width: 100%;
    text-align: center;
    margin-bottom: 20px;
    font-size: 24px;
    font-weight: 500;
    color: var(--primary-color);
  }

  .topology-info-container {
    max-width: 800px;
    margin: 0 auto;

    .topology-info-card {
      background-color: var(--card-bg-color);
      border-radius: 12px;
      padding: 20px;
      box-shadow: var(--box-shadow);
      border: 1px solid var(--border-color);
      margin-top: 20px;

      .info-item {
        display: flex;
        align-items: center;
        margin-bottom: 12px;
        padding-bottom: 12px;
        border-bottom: 1px solid var(--border-color);
        font-size: 16px;

        .info-label {
          width: 180px;
          font-weight: 500;
          color: var(--text-primary-color);
          margin-right: 20px;
          border-right: 2px solid var(--border-color);
        }

        .info-value {
          flex: 1;
          margin-left: 20px;
          color: var(--text-secondary-color);
          word-break: break-all;

          a {
            color: var(--primary-color);
            text-decoration: none;
            font-weight: 500;
            text-decoration: underline;

            &:hover {
              color: var(--text-secondary-color);
            }
          }

          .detail-button {
            margin-left: 20px !important;
            font-size: 15px;
            background-color: var(--primary-color);
            color: var(--text-reverse-color);
            border: none;

            &:hover {
              background-color: var(--secondary-color);
              color: var(--text-primary-color);
            }
          }
        }

        &:last-child {
          margin-bottom: 0;
          padding-bottom: 0;
          border-bottom: none;
        }
      }
    }
  }

  :deep(.el-table) {
    font-size: 16px;

    .tag {
      font-size: 16px;
    }

    .el-table__header-wrapper {
      th {
        background-color: var(--card-bg-color);
        color: var(--text-secondary-color);
        border-bottom: 1px solid var(--border-color);
      }
    }

    .el-table__body-wrapper {
      tr {
        td {
          color: var(--text-secondary-color);
        }
      }
    }
  }
}
</style>
