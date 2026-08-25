const imageModules = import.meta.glob("../assets/imgs/**/*.{png,svg}", {
  eager: true,
  import: "default",
});

const getImage = (path: string, suffix: string): string => {
  const fullPath = `../assets/imgs/${path}.${suffix}`;
  const imageUrl = imageModules[fullPath];

  if (!imageUrl) {
    console.warn(`Image not found: ${fullPath}`);
    return "";
  }

  return imageUrl as string;
};

const getPng = (path: string) => getImage(path, "png");
const getSvg = (path: string) => getImage(path, "svg");

export { getPng, getSvg };
