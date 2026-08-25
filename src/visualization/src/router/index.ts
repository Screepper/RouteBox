import { createRouter, createWebHistory } from "vue-router";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      name: "default",
      redirect: "/main",
    },
    {
      path: "/main",
      name: "main",
      component: () => import("@/views/Main.vue"),
      children: [
        {
          path: "topo",
          name: "topo",
          redirect: "/main/topo/sernes",
          children: [
            {
              path: "sernes",
              name: "sernes",
              component: () => import("@/components/topo/SERNES.vue"),
            },
            {
              path: "management",
              name: "topoManage",
              component: () => import("@/components/topo/TopoManage.vue"),
            },
          ],
        },
        {
          path: "evaluate",
          name: "evaluate",
          redirect: "/main/evaluate/attack",
          children: [
            {
              path: "attack",
              name: "attackInjection",
              component: () => import("@/components/evaluate/Attack.vue"),
            },
            {
              path: "monitor",
              name: "dataMonitor",
              component: () => import("@/components/evaluate/DataMonitor.vue"),
            },
          ],
        },
        {
          path: "detect",
          name: "detect",
          redirect: "/main/detect/realtime",
          children: [
            {
              path: "realtime",
              name: "realTimeDetection",
              component: () => import("@/components/detect/RealTimeDetect.vue"),
              meta: {
                keepAlive: true,
              },
            },
            {
              path: "histories",
              name: "detectHistories",
              component: () =>
                import("@/components/detect/DetectHistories.vue"),
            },
          ],
        },
      ],
    },
  ],
});

export default router;
