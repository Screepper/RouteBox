interface Menu {
  name: string;
  icon: string;
  path?: string;
  children?: Menu[];
}

export type { Menu };
