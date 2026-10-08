import L from 'leaflet'

/**
 * 天地图 WMTS XYZ 图层（CGCS2000 ≈ WGS-84，无人机 OSD 坐标无需转换即可叠图）。
 * img_w/cia_w = 影像底图+注记；vec_w/cva_w = 矢量底图+注记。
 */
export function tdtBaseLayers(tk: string): Record<string, L.LayerGroup> {
  const mk = (type: string) =>
    L.tileLayer(`https://t{s}.tianditu.gov.cn/DataServer?T=${type}&x={x}&y={y}&l={z}&tk=${tk}`, {
      subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'],
      maxZoom: 18
    })
  return {
    影像: L.layerGroup([mk('img_w'), mk('cia_w')]),
    矢量: L.layerGroup([mk('vec_w'), mk('cva_w')])
  }
}
