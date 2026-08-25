import { defineStore } from "pinia";
import { ref } from "vue";

export const useStatesStore = defineStore(
  "StatesStore",
  () => {
    // State
    const currentChoose = ref("");
    const opendMenus = ref<string[]>([]);
    const collapsed = ref(false);
    const lang = ref("en");
    const darkTheme = ref(false);

    // Actions
    function setCurrentChoose(value: string) {
      currentChoose.value = value;
    }

    function setOpendMenus(value: string[]) {
      opendMenus.value = value;
    }

    function getCurrentChoose(): string {
      return currentChoose.value;
    }

    function getOpendMenus(): string[] {
      return opendMenus.value;
    }

    function setCollapsed(value: boolean) {
      collapsed.value = value;
    }

    function getCollapsed(): boolean {
      return collapsed.value; 
    }

    function setLang(value: string) {
      lang.value = value;
    }

    function getLang(): string {
      return lang.value;
    }

    function setDarkTheme(value: boolean) {
      darkTheme.value = value;
    }

    function getDarkTheme(): boolean {
      return darkTheme.value;
    }

    return {
      currentChoose,
      opendMenus,
      collapsed,
      lang,
      darkTheme,
      setCurrentChoose,
      setOpendMenus,
      getCurrentChoose,
      getOpendMenus,
      setCollapsed,
      getCollapsed,
      setLang,
      getLang,
      setDarkTheme,
      getDarkTheme,
    };
  },
  {
    persist: true
  }
);
