<template>
  <div class="bg">
    <div class="left" :class="{ collapsed: isCollapsed }">
      <div class="logo-area">
        <img class="logo" src="@/assets/imgs/logo.png" />
        <span class="name" v-if="!isCollapsed">RouteBox</span>
        <div class="toggle-btn" @click="toggleMenu">
          {{ !isCollapsed ? "<" : ">" }}
        </div>
      </div>
      <el-menu
        class="menu-bar"
        :default-active="currentChoose"
        :default-openeds="opendMenus"
        @open="(path: string) => handleMenuOp(path, true)"
        @close="(path: string) => handleMenuOp(path, false)"
      >
        <template v-for="(menu, _) in menus" :index="menu.name">
          <el-sub-menu class="menu-item" :index="menu.name">
            <template #title>
              <img class="icon" :src="menu.icon" />
              <span class="name">{{ $t(menu.name) }}</span>
            </template>
            <el-menu-item
              v-for="(child, _) in menu.children"
              :class="{ 'active-menu': currentChoose === child.path }"
              :index="child.path"
              @click="changeMenuItem(child.path as string)"
            >
              <template #title>
                <img class="icon" :src="child.icon" />
                <span class="name">{{ $t(child.name) }}</span>
              </template>
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </div>
    <div class="right">
      <div class="header">
        <span class="empty-block"></span>
        <span class="title">{{ $t("main.title") }}</span>
        <div class="right-block">
          <el-switch
            class="switch-style"
            v-model="darkTheme"
            :active-action-icon="Moon"
            :inactive-action-icon="Sunny"
            @change="changeTheme"
          >
          </el-switch>
          <el-dropdown class="lang" placement="bottom-start">
            <el-button> {{ $t("main.lang") }} </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="changeLang('zh')"
                  >中文</el-dropdown-item
                >
                <el-dropdown-item @click="changeLang('en')"
                  >English</el-dropdown-item
                >
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
      <div class="content">
        <div v-if="!currentChoose" class="default-page">
          <img src="../assets/imgs/logo.png" alt="" />
          <div class="default-content">
            <h1>RouteBox</h1>
            <p>{{ $t("pages.default.description") }}</p>
          </div>
        </div>

        <router-view v-else v-slot="{ Component }">
          <keep-alive>
            <component
              v-if="$route.meta.keepAlive"
              :is="Component"
              :key="$route.name"
            ></component>
          </keep-alive>
          <component
            v-if="!$route.meta.keepAlive"
            :is="Component"
            :key="$route.name"
          ></component>
        </router-view>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onBeforeMount, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Sunny, Moon } from "@element-plus/icons-vue";
import { useStatesStore } from "@/stores/statesStore";
import { useI18n } from "vue-i18n";
import { getPng } from "@/utils/FileUtil";
import type { Menu } from "@/ifaces/main";

const route = useRoute();
const router = useRouter();

const statesStore = useStatesStore();
const { locale } = useI18n(); // language
const darkTheme = ref(statesStore.getDarkTheme()); // theme
const isCollapsed = ref(statesStore.getCollapsed()); // menu collapse
const currentChoose = ref(statesStore.getCurrentChoose()); // current choose menu
const opendMenus = ref(statesStore.getOpendMenus()); // opened menus

// Menus
const menus: Menu[] = [
  {
    name: "leftmenu.topo",
    icon: getPng("menu/topo"),
    children: [
      {
        name: "pages.sernes.title",
        path: "/main/topo/sernes",
        icon: getPng("menu/SERNES"),
      },
      {
        name: "pages.topoManage.title",
        path: "/main/topo/management",
        icon: getPng("menu/topoManage"),
      },
    ],
  },
  {
    name: "leftmenu.evaluate",
    icon: getPng("menu/evaluate"),
    children: [
      {
        name: "pages.attack.title",
        path: "/main/evaluate/attack",
        icon: getPng("menu/attack"),
      },
      {
        name: "pages.dataMonitor.title",
        path: "/main/evaluate/monitor",
        icon: getPng("menu/monitor"),
      },
    ],
  },
  {
    name: "leftmenu.detect",
    icon: getPng("menu/detect"),
    children: [
      {
        name: "pages.realTimeDetect.title",
        path: "/main/detect/realtime",
        icon: getPng("menu/realtime"),
      },
      {
        name: "pages.detectHistories.title",
        path: "/main/detect/histories",
        icon: getPng("menu/histories"),
      },
    ],
  },
];

// Change Global Theme
const changeTheme = (dark: boolean) => {
  const doc = document.documentElement;

  if (dark) {
    doc.classList.add("dark");
  } else {
    doc.classList.remove("dark");
  }

  statesStore.setDarkTheme(dark);
};

// Change Global Language
const changeLang = (lang: string) => {
  locale.value = lang;

  statesStore.setLang(lang);
};

// Change Current Choose Menu
const changeMenuItem = (path: string) => {
  if (currentChoose.value !== path) {
    router.push({ path });
  }
};

const handleMenuOp = (path: string, isAdd: boolean) => {
  if (isAdd) {
    if (!opendMenus.value.includes(path)) {
      opendMenus.value.push(path);
    }
  } else {
    opendMenus.value = opendMenus.value.filter((menuPath) => menuPath !== path);
  }

  statesStore.setOpendMenus(opendMenus.value);
};

const toggleMenu = () => {
  isCollapsed.value = !isCollapsed.value;

  statesStore.setCollapsed(isCollapsed.value);
};

watch(
  () => route.path,
  (newPath) => {
    if (newPath !== "/main") {
      currentChoose.value = newPath;

      statesStore.setCurrentChoose(newPath);
    } else if (
      statesStore.getCurrentChoose() &&
      statesStore.getCurrentChoose() !== "/main"
    ) {
      router.push({ path: statesStore.getCurrentChoose() });
    }
  },
  { immediate: true },
);

onBeforeMount(() => {
  changeTheme(darkTheme.value);
  changeLang(statesStore.getLang());
});
</script>
<style lang="scss" scoped>
.el-dropdown-menu {
  border: 1px solid var(--border-color);
  background-color: var(--card-bg-color);
  padding: 0;
  min-width: 80px;

  :deep(.el-dropdown-menu__item) {
    padding: 5px 10px;
    font-size: 13px;
    font-weight: 500;
    display: flex;
    justify-content: center;
    align-items: center;

    &:hover {
      background-color: var(--hover-bg-color);
      color: var(--text-primary-color);
    }
  }
}

:deep(.el-menu) {
  background-color: var(--bg-color);
}

.bg {
  width: 100%;
  min-height: 100vh;
  display: flex;
  background-color: var(--base-bg-color);

  .left {
    max-width: 280px;
    width: 280px;
    border-right: 1px solid var(--border-color);
    background-color: var(--sidebar-bg-color);
    border-radius: 0 15px 15px 0;
    margin: 8px 0 8px 8px;
    transition: all 0.3s ease;
    user-select: none;

    .logo-area {
      position: relative;
      display: flex;
      align-items: center;
      gap: 10px;
      height: 120px;
      border-bottom: 1px solid var(--border-color);

      .logo {
        margin-left: 15px;
        width: 100px;
        height: 100px;
        user-select: none;
      }

      .name {
        font-size: 22px;
        font-weight: 700;
        color: var(--primary-color);
      }

      .toggle-btn {
        position: absolute;
        right: 5px;
        padding: 0 10px;
        font-size: 26px;
        color: var(--text-secondary-color);
        cursor: pointer;
        transition: all 0.3s ease;
        background-color: var(--card-bg-color);

        &:hover {
          color: var(--primary-color);
          scale: 1.1;
        }
      }
    }

    &.collapsed {
      width: 100px;
      max-width: 100px;

      .logo-area {
        height: 60px;

        .logo {
          display: none;
        }

        .name {
          display: none;
        }

        .toggle-btn {
          right: 30px;
        }
      }

      .menu-bar {
        .menu-item {
          .name {
            display: none;
          }

          .el-sub-menu__title,
          .el-menu-item {
            padding: 0 15px;
            margin: 0 10px;
          }

          .icon {
            width: 30px;
          }
        }
      }
    }
  }

  .menu-bar {
    background-color: var(--sidebar-bg-color);
    border: none;
    user-select: none;
    padding: 15px 0;
    transition: all 0.3s ease;

    .menu-item {
      .icon {
        width: 35px;
        margin-right: 15px;
      }

      .name {
        font-size: 15px;
        color: var(--text-secondary-color);
        letter-spacing: 0.5px;
      }

      .el-sub-menu__title,
      .el-menu-item {
        padding: 0 25px;
        margin: 0 20px;
        transition: all 0.3s ease;
      }

      .el-sub-menu__title:hover,
      .el-menu-item:hover {
        background-color: var(--hover-bg-color);
        transform: translateX(5px);
      }

      .active-menu {
        background: linear-gradient(
          135deg,
          var(--primary-color),
          var(--bg-color)
        );

        .name {
          font-weight: 500;
          color: var(--text-primary-color);
        }

        .icon {
          filter: brightness(0) invert(1);
        }
      }

      :deep(.el-sub-menu__icon-arrow) {
        color: var(--text-primary-color);
      }
    }
  }

  .right {
    flex: 1;
    margin: 8px 8px 8px 0;
    border-radius: 15px;
    overflow: hidden;
    background-color: var(--bg-color);

    .header {
      height: 50px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      background-color: var(--card-bg-color);
      border-bottom: 1px solid var(--border-color);
      padding: 0 30px;
      transition: all 0.3s ease;

      .empty-block {
        margin-left: 0;
        margin-right: 0;
      }

      .title {
        font-size: 24px;
        font-weight: 700;
        color: var(--text-primary-color);
        white-space: nowrap;
        overflow: hidden;
      }

      .right-block {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 15px;

        :deep(.switch-style) {
          .el-switch__core {
            width: 50px;
            height: 20px;
            border: 1px solid var(--border-color);
            border-radius: 20px;
            box-shadow: inset var(--box-shadow);
            background-color: var(--switch-bg-color);
          }

          .el-switch__action {
            width: 24px;
            height: 24px;
            margin-left: -5px;
            border: 1px solid var(--border-color);
            border-radius: 12px;
            color: var(--primary-color);
            background-color: var(--switch-button-color);
          }
        }

        :deep(.el-dropdown.lang) {
          .el-button {
            width: 56px;
            height: 28px;
            font-size: 13px;
            font-weight: 500;
            border-radius: 10px;
            border: 1px solid var(--border-color);
            background-color: var(--button-bg-color);
            box-shadow: var(--box-shadow);
            color: var(--text-secondary-color);

            &:hover {
              transform: translateY(-2px);
            }
          }
        }
      }
    }

    .content {
      width: 100%;
      height: calc(100vh - 70px);
      overflow: auto;
    }

    .default-page {
      width: 100%;
      height: 100%;
      display: flex;
      flex-direction: column;
      justify-content: space-evenly;
      align-items: center;
      background-color: var(--bg-color);

      img {
        width: 250px;
        height: 250px;
      }

      .default-content {
        text-align: center;
        padding: 40px;
        border-radius: 15px;
        background-color: var(--card-bg-color);
        box-shadow: var(--box-shadow);

        h1 {
          font-size: 32px;
          font-weight: 700;
          color: var(--primary-color);
          margin-bottom: 20px;
        }

        p {
          font-size: 18px;
          color: var(--text-secondary-color);
          line-height: 1.5;
        }
      }
    }
  }
}
</style>
