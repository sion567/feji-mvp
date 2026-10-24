import type { StyleSpecification, RasterSourceSpecification } from 'maplibre-gl'

const SUBDOMAINS = ['0', '1', '2', '3', '4', '5', '6', '7']

/**
 * 天地图 WMTS XYZ 图层（CGCS2000 ≈ WGS-84，无人机 OSD 坐标无需转换即可叠图）。
 * MapLibre 不支持 Leaflet 的 {s} 子域名模板，需展开为完整 URL 列表。
 */
function tdtRaster(tk: string, type: string): RasterSourceSpecification {
  return {
    type: 'raster',
    tiles: SUBDOMAINS.map(
      (s) => `https://t${s}.tianditu.gov.cn/DataServer?T=${type}&x={x}&y={y}&l={z}&tk=${tk}`
    ),
    tileSize: 256,
    maxzoom: 18
  }
}

/** 影像/矢量两套天地图底图的 MapLibre style（含对应注记层） */
export function tdtStyle(tk: string, base: '影像' | '矢量' = '影像'): StyleSpecification {
  const [baseType, noteType] = base === '影像' ? ['img_w', 'cia_w'] : ['vec_w', 'cva_w']
  return {
    version: 8,
    sources: {
      'tdt-base': tdtRaster(tk, baseType),
      'tdt-note': tdtRaster(tk, noteType)
    },
    layers: [
      { id: 'tdt-base', type: 'raster', source: 'tdt-base' },
      { id: 'tdt-note', type: 'raster', source: 'tdt-note' }
    ]
  }
}
