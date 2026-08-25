// Declare Vue component type
declare module "*.vue" {
  import type { DefineComponent, SlotsType } from "vue";
  // Allow any props and slots
  const component: DefineComponent<
    any,
    any,
    any,
    any,
    any,
    any,
    any,
    any,
    any,
    any,
    any,
    any,
    any // SlotType, allow any slot type
  >;
  export default component;
}

declare module "element-plus*";
declare module "stores";
declare module "i18n";
declare module "utils";
declare module "common";
declare module "lang";
declare module "request";

// Declare style file type
declare module "*.scss" {
  const classes: { [key: string]: string };
  export default classes;
}

// Declare Sass file type
declare module "*.sass" {
  const classes: { [key: string]: string };
  export default classes;
}

// Declare CSS file type
declare module "*.css" {
  const classes: { [key: string]: string };
  export default classes;
}
