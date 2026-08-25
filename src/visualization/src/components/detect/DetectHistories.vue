<template>
  <div class="page-container">
    <div class="detect-histories-container">
      <el-card class="detect-records-section">
        <h2 class="section-title">{{ $t("pages.detectHistories.title") }}</h2>
        <el-table :data="detectResults" border stripe>
          <el-table-column
            prop="networkId"
            :label="$t('pages.detectHistories.columns.networkId')"
            width="120"
          />
          <el-table-column
            prop="dataSource"
            :label="$t('pages.detectHistories.columns.dataSource')"
            width="120"
          />
          <el-table-column
            prop="timestamp"
            :label="$t('pages.detectHistories.columns.timestamp')"
            width="200"
          >
            <template #default="scope">
              {{ formatTimestamp(scope.row.timestamp) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="isAnomaly"
            :label="$t('pages.detectHistories.columns.isAnomaly')"
            width="120"
          >
            <template #default="scope">
              <el-tag
                :type="scope.row.isAnomaly ? 'danger' : 'success'"
                size="large"
              >
                {{
                  scope.row.isAnomaly
                    ? $t("pages.detectHistories.status.anomaly")
                    : $t("pages.detectHistories.status.normal")
                }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('pages.detectHistories.columns.action')">
            <template #default="scope">
              <el-button
                class="op-btn"
                v-if="scope.row.isAnomaly"
                type="primary"
                @click="showDetails(scope.row)"
              >
                {{ $t("pages.detectHistories.buttons.details") }}
              </el-button>
            </template>
          </el-table-column>
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

      <el-dialog
        v-model="detailsDialogVisible"
        :title="$t('pages.detectHistories.dialog.title')"
        width="80%"
        :before-close="() => (detailsDialogVisible = false)"
      >
        <div v-if="selectedResult" class="details-content">
          <div class="anomaly-section">
            <div class="section-header">
              {{ $t("pages.detectHistories.dialog.anomalyInfo") }}
            </div>
            <div class="card-grid">
              <div class="anomaly-info-card">
                <div class="card-title">
                  {{ $t("pages.detectHistories.dialog.topoInfo") }}
                </div>
                <div class="anomaly-info-content">
                  {{ selectedResult.topologyInfo }}
                </div>
              </div>
              <div class="anomaly-info-card">
                <div class="card-title">
                  {{ $t("pages.detectHistories.dialog.systemNetworkAnomaly") }}
                </div>
                <div class="anomaly-info-content">
                  {{ selectedResult.systemNetworkAnomaly }}
                </div>
              </div>
              <div class="anomaly-info-card">
                <div class="card-title">
                  {{ $t("pages.detectHistories.dialog.bgpAnomaly") }}
                </div>
                <div class="anomaly-info-content">
                  {{ selectedResult.bgpAnomaly }}
                </div>
              </div>
              <div class="anomaly-info-card">
                <div class="card-title">
                  {{ $t("pages.detectHistories.dialog.routerAnomaly") }}
                </div>
                <div class="anomaly-info-content">
                  {{ selectedResult.routerAnomaly }}
                </div>
              </div>
              <div class="anomaly-info-card">
                <div class="card-title">
                  {{ $t("pages.detectHistories.dialog.pingAnomaly") }}
                </div>
                <div class="anomaly-info-content">
                  {{ selectedResult.pingAnomaly }}
                </div>
              </div>
              <div class="anomaly-info-card">
                <div class="card-title">
                  {{ $t("pages.detectHistories.dialog.topoAnomaly") }}
                </div>
                <div class="anomaly-info-content">
                  {{ selectedResult.topologyAnomaly }}
                </div>
              </div>
            </div>
          </div>

          <el-divider class="divider"></el-divider>

          <div class="anomaly-section">
            <div class="section-header">
              {{ $t("pages.detectHistories.dialog.evidence") }}
            </div>
            <div class="card-grid">
              <div class="evidence-card">
                <div class="card-title">
                  {{ $t("pages.detectHistories.dialog.directEvidence") }}
                </div>
                <ul class="evidence-list">
                  <li
                    v-for="(evidence, idx) in selectedResult.directEvidence"
                    :key="idx"
                  >
                    {{ evidence }}
                  </li>
                </ul>
              </div>
              <div class="evidence-card">
                <div class="card-title">
                  {{ $t("pages.detectHistories.dialog.indirectEvidence") }}
                </div>
                <ul class="evidence-list">
                  <li
                    v-for="(evidence, idx) in selectedResult.indirectEvidence"
                    :key="idx"
                  >
                    {{ evidence }}
                  </li>
                </ul>
              </div>
            </div>
          </div>

          <div class="anomaly-section">
            <div class="section-header">
              {{ $t("pages.detectHistories.dialog.possibilities") }}
            </div>
            <div class="custom-table">
              <div class="table-header">
                <div class="table-cell method-header">
                  {{ $t("pages.detectHistories.dialog.method") }}
                </div>
                <div class="table-cell possibility-header">
                  {{ $t("pages.detectHistories.dialog.possibility") }}
                </div>
                <div class="table-cell rationale-header">
                  {{ $t("pages.detectHistories.dialog.rationale") }}
                </div>
              </div>
              <div class="table-body">
                <div
                  v-for="(item, index) in selectedResult.possibilities"
                  :key="index"
                  class="table-row"
                >
                  <div class="table-cell method-cell">{{ item.method }}</div>
                  <div class="table-cell possibility-cell">
                    {{ item.possibility }}
                  </div>
                  <div class="table-cell rationale-cell">
                    {{ item.rationale }}
                  </div>
                </div>
              </div>
            </div>
          </div>

          <el-divider class="divider"></el-divider>

          <div class="anomaly-section">
            <div class="section-header">
              {{ $t("pages.detectHistories.dialog.attackType") }}
            </div>
            <div class="section-content">{{ selectedResult.attackType }}</div>
          </div>

          <div class="anomaly-section">
            <div class="section-header">
              {{ $t("pages.detectHistories.dialog.timeRange") }}
            </div>
            <div class="section-content">
              {{ selectedResult.anomalyStart }} -
              {{ selectedResult.anomalyEnd }}
            </div>
          </div>

          <div class="anomaly-section">
            <div class="section-header">
              {{ $t("pages.detectHistories.dialog.participants") }}
            </div>
            <div class="card-grid">
              <div class="participant-card">
                <div class="card-title">
                  {{ $t("pages.detectHistories.dialog.attackers") }}
                </div>
                <div class="node-tags">
                  <el-tag
                    v-if="
                      selectedResult.attackers &&
                      selectedResult.attackers.length > 0
                    "
                    v-for="(id, idx) in selectedResult.attackers"
                    :key="idx"
                    type="danger"
                  >
                    {{ id }}
                  </el-tag>
                  <div v-else class="no-participant">None</div>
                </div>
              </div>
              <div class="participant-card">
                <div class="card-title">
                  {{ $t("pages.detectHistories.dialog.victims") }}
                </div>
                <div class="node-tags">
                  <el-tag
                    v-if="
                      selectedResult.victims &&
                      selectedResult.victims.length > 0
                    "
                    v-for="(id, idx) in selectedResult.victims"
                    :key="idx"
                    type="warning"
                  >
                    {{ id }}
                  </el-tag>
                  <div v-else class="no-participant">None</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-dialog>
    </div>
  </div>
</template>

<script setup lang="ts">
import { getCurrentInstance, ref, reactive, onMounted } from "vue";
import type { DetectResultLog } from "@/ifaces/detect";
import type { PageQuery } from "@/ifaces/common";

const proxy = getCurrentInstance()?.proxy as any;

const pageQuery = reactive<PageQuery>({
  currentPage: 1,
  pageSize: 10,
});
const totalRecords = ref(0);

const detectResults = ref<DetectResultLog[]>([]);

const detailsDialogVisible = ref(false);
const selectedResult = ref<DetectResultLog | null>(null);

// Format timestamp to readable time
const formatTimestamp = (timestamp: number) =>
  new Date(timestamp * 1000).toLocaleString();

const handleSizeChange = (size: number) => {
  pageQuery.pageSize = size;
  pageQuery.currentPage = 1;

  loadDetectResults();
};

const handleCurrentChange = (current: number) => {
  pageQuery.currentPage = current;

  loadDetectResults();
};

const showDetails = (result: DetectResultLog) => {
  selectedResult.value = result;
  detailsDialogVisible.value = true;
};

// Get detect results from backend
const loadDetectResults = async () => {
  const result = await proxy.Request({
    url: proxy.Api.loadDetectRecords,
    data: pageQuery,
  });

  if (!result) return;

  detectResults.value = result.data.records || [];
  totalRecords.value = result.data.total || 0;
};

onMounted(() => loadDetectResults());
</script>

<style lang="scss" scoped>
.page-container {
  padding: 15px;
  background-color: var(--bg-color);

  .detect-histories-container {
    max-width: 1000px;
    margin: 0 auto;

    .detect-records-section {
      background-color: var(--card-bg-color);
      border-radius: 12px;
      padding: 10px;
      box-shadow: var(--box-shadow);
      border: 1px solid var(--border-color);
      margin: 40px auto;
      max-width: 100%;

      .section-title {
        font-size: 22px;
        font-weight: 600;
        color: var(--primary-color);
        margin-bottom: 15px;
      }

      .op-btn {
        background-color: var(--primary-color);
        color: var(--text-reverse-color);
        border: none;

        &:hover {
          background-color: var(--secondary-color);
          color: var(--text-primary-color);
        }
      }

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

    :deep(.el-dialog) {
      background-color: var(--card-bg-color);
      border-radius: 12px;
      border: 1px solid var(--border-color);
      box-shadow: var(--box-shadow);

      :deep(.el-dialog__header) {
        background-color: var(--card-bg-color);
        border-bottom: 1px solid var(--border-color);

        :deep(.el-dialog__title) {
          color: var(--text-primary-color);
          font-size: 18px;
          font-weight: 600;
        }
      }

      :deep(.el-dialog__body) {
        padding: 20px;
        color: var(--text-secondary-color);
      }

      :deep(.el-dialog__footer) {
        background-color: var(--card-bg-color);
        border-top: 1px solid var(--border-color);
      }
    }

    .details-content {
      .anomaly-section {
        .section-header {
          font-size: 18px;
          font-weight: 600;
          color: var(--el-color-danger);
          padding: 10px 0;
        }

        .section-content {
          padding: 10px;
          border: 1px solid var(--border-color);
          background-color: var(--bg-color);
          color: var(--text-secondary-color);
          font-size: 16px;
          font-weight: 500;
          box-shadow: var(--box-shadow);
        }

        .anomaly-info-card {
          padding: 15px;
          border: 1px solid var(--border-color);
          background-color: var(--bg-color);
          border-radius: 8px;
          max-height: 500px;
          overflow: auto;
          box-shadow: inset var(--box-shadow);
          font-size: 16px;
          font-weight: 500;

          .anomaly-info-content {
            margin-top: 5px;
            color: var(--text-secondary-color);
            white-space: pre-wrap;
            word-break: break-word;
          }
        }

        .custom-table {
          border: 1px solid var(--border-color);
          background-color: var(--card-bg-color);
          overflow: hidden;
          box-shadow: var(--box-shadow);

          .table-header {
            display: flex;
            background-color: var(--hover-bg-color);
            border-bottom: 1px solid var(--border-color);
            font-weight: 600;

            .table-cell {
              padding: 10px;
              font-size: 16px;
              text-align: center;
              color: var(--primary-color);

              &:last-child {
                border-right: none;
              }
            }

            .method-header {
              width: 200px;
            }

            .possibility-header {
              width: 100px;
            }

            .rationale-header {
              flex: 1;
            }
          }

          .table-body {
            .table-row {
              display: flex;
              border-bottom: 1px solid var(--border-color);
              background-color: var(--bg-color);

              &:last-child {
                border-bottom: none;
              }

              .table-cell {
                padding: 10px;
                font-size: 16px;
                word-break: break-word;
                color: var(--text-secondary-color);
              }

              .method-cell {
                width: 200px;
                text-align: center;
              }

              .possibility-cell {
                width: 100px;
                font-weight: bold;
                text-align: center;
              }

              .rationale-cell {
                flex: 1;
              }
            }
          }
        }

        .evidence-card {
          padding: 10px;
          border: 1px solid var(--border-color);
          border-radius: 12px;
          box-shadow: var(--box-shadow);
          background-color: var(--bg-color);
          font-size: 16px;

          .evidence-list {
            list-style-type: decimal;
            padding-left: 20px;

            li {
              margin-bottom: 5px;
              line-height: 1.5;
              color: var(--text-secondary-color);
            }
          }
        }

        .participant-card {
          padding: 10px;
          border: 1px solid var(--border-color);
          border-radius: 12px;
          box-shadow: var(--box-shadow);
          background-color: var(--bg-color);

          .node-tags {
            display: flex;
            flex-wrap: wrap;
            gap: 5px;

            :deep(.el-tag) {
              box-shadow: var(--box-shadow-tiny);
            }
          }

          .no-participant {
            padding: 10px;
            font-size: 14px;
            color: var(--el-color-success);
          }
        }
      }

      .divider {
        border-color: var(--primary-color);
        margin: 20px 0 5px 0;
      }
    }
  }
}

.card-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;

  .card-title {
    font-weight: 600;
    font-size: 16px;
    color: var(--primary-color);
    padding-bottom: 5px;
    margin-bottom: 10px;
    border-bottom: 1px solid var(--border-color);
  }
}
</style>
