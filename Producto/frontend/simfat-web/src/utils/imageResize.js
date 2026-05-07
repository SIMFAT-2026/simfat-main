const DEFAULT_MAX_SIDE = 1024;
const DEFAULT_QUALITY = 0.76;
const DEFAULT_MIME_TYPE = 'image/webp';

function readAsDataURL(file) {
  return new Promise((resolve, reject) => {
    const reader = new window.FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(new Error('No fue posible leer la imagen.'));
    reader.readAsDataURL(file);
  });
}

function loadImage(dataUrl) {
  return new Promise((resolve, reject) => {
    const image = new window.Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error('No fue posible cargar la imagen.'));
    image.src = dataUrl;
  });
}

function canvasToBlob(canvas, mimeType, quality) {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (!blob) {
        reject(new Error('No fue posible convertir la imagen.'));
        return;
      }
      resolve(blob);
    }, mimeType, quality);
  });
}

function buildOutputDimensions(width, height, maxSide) {
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
    return { width: maxSide, height: maxSide };
  }

  const longest = Math.max(width, height);
  if (longest <= maxSide) {
    return { width, height };
  }

  const ratio = maxSide / longest;
  return {
    width: Math.max(1, Math.round(width * ratio)),
    height: Math.max(1, Math.round(height * ratio))
  };
}

function outputName(name = 'imagen.webp') {
  const raw = name.includes('.') ? name.slice(0, name.lastIndexOf('.')) : name;
  return `${raw || 'imagen'}.webp`;
}

export async function resizeImageFile(file, options = {}) {
  const maxSide = Number(options.maxSide || DEFAULT_MAX_SIDE);
  const quality = Number(options.quality || DEFAULT_QUALITY);
  const mimeType = options.mimeType || DEFAULT_MIME_TYPE;

  if (!(file instanceof window.File)) {
    throw new Error('Archivo invalido para redimension.');
  }

  if (!file.type.startsWith('image/')) {
    return file;
  }

  const dataUrl = await readAsDataURL(file);
  const image = await loadImage(dataUrl);
  const dims = buildOutputDimensions(image.naturalWidth, image.naturalHeight, maxSide);

  const canvas = window.document.createElement('canvas');
  canvas.width = dims.width;
  canvas.height = dims.height;

  const ctx = canvas.getContext('2d');
  if (!ctx) {
    throw new Error('No fue posible preparar contexto de imagen.');
  }

  ctx.drawImage(image, 0, 0, dims.width, dims.height);
  const blob = await canvasToBlob(canvas, mimeType, quality);

  return new window.File([blob], outputName(file.name), {
    type: mimeType,
    lastModified: Date.now()
  });
}

export async function resizeImagesBatch(files = [], options = {}) {
  const safeFiles = Array.isArray(files) ? files : [];
  const resized = [];
  for (const file of safeFiles) {
    resized.push(await resizeImageFile(file, options));
  }
  return resized;
}
