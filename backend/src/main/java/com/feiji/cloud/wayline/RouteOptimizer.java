package com.feiji.cloud.wayline;

import java.util.ArrayList;
import java.util.List;

/**
 * 选点航线点序优化（开环 TSP：起点固定，不要求回起点）。
 *
 * WPML 按航点写入顺序逐点执行、运行时不重排，因此「优化」发生在生成 KMZ 之前——
 * 排好写入顺序即排好飞行顺序。距离度量为 3D 欧氏（haversine 水平距离 + 高度差），
 * 设计依据见根目录《路径算法设计.md》。
 *
 * 策略（按点数自动切换，调用方无感）：
 * - N ≤ {@link #BRUTE_FORCE_LIMIT}：暴力全排列，全局最优
 * - N >  {@link #BRUTE_FORCE_LIMIT}：最近邻构造初始解 + 2-opt 迭代消交叉（开环变体，无回起点闭合边）
 */
public final class RouteOptimizer {

    /** 暴力全排列的点数上限（10! = 362 万排列，毫秒级） */
    public static final int BRUTE_FORCE_LIMIT = 10;

    private static final double EARTH_RADIUS_M = 6_371_008.8;

    private RouteOptimizer() {
    }

    /** 航点（height 可为 NaN 表示未知，按 0 高差计） */
    public record Point(double lat, double lng, double height) {
    }

    public enum Strategy {
        BRUTE_FORCE, NN_2OPT
    }

    /** 排序结果：order 为点的下标排列（0..n-1），totalDistanceMeters 含起点→首点段 */
    public record Result(List<Integer> order, double totalDistanceMeters, Strategy strategy) {
    }

    public static Result optimize(Point start, List<Point> points) {
        return optimize(start, points, null);
    }

    /**
     * strategy 为 null 时按点数自动切换；显式指定 NN_2OPT 可对小规模点集强制近似策略（测试/对比用），
     * 显式 BRUTE_FORCE 超过 {@link #BRUTE_FORCE_LIMIT} 时回退 NN_2OPT 防止排列爆炸。
     */
    public static Result optimize(Point start, List<Point> points, Strategy strategy) {
        if (points == null || points.isEmpty()) {
            return new Result(List.of(), 0, Strategy.BRUTE_FORCE);
        }
        int n = points.size();
        // 预计算距离矩阵：0 号为起点，1..n 为待排点
        double[][] d = new double[n + 1][n + 1];
        d[0][0] = 0;
        for (int i = 1; i <= n; i++) {
            d[0][i] = d[i][0] = distance(start, points.get(i - 1));
            d[i][i] = 0;
            for (int j = i + 1; j <= n; j++) {
                d[i][j] = d[j][i] = distance(points.get(i - 1), points.get(j - 1));
            }
        }
        boolean brute = strategy == Strategy.BRUTE_FORCE
                || (strategy == null && n <= BRUTE_FORCE_LIMIT);
        return brute ? bruteForce(n, d) : nearestNeighbor2Opt(n, d);
    }

    /** 3D 欧氏距离：haversine 水平 + 高度差（高度未知按 0） */
    public static double distance(Point a, Point b) {
        double dh = Double.isNaN(a.height()) || Double.isNaN(b.height())
                ? 0 : b.height() - a.height();
        double horizontal = haversineMeters(a.lat(), a.lng(), b.lat(), b.lng());
        return Math.sqrt(horizontal * horizontal + dh * dh);
    }

    /** haversine 球面距离（米），巡检几十公里尺度误差可忽略 */
    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    // ---------------- 暴力全排列 ----------------

    private static Result bruteForce(int n, double[][] d) {
        int[] best = new int[n];
        for (int i = 0; i < n; i++) {
            best[i] = i;
        }
        int[] current = new int[n];
        System.arraycopy(best, 0, current, 0, n);
        double bestCost = pathCost(current, d);

        // Heap 算法枚举全排列
        int[] c = new int[n];
        for (int i = 0; i < n; ) {
            if (c[i] < i) {
                int swapAt = i % 2 == 0 ? 0 : c[i];
                int tmp = current[i];
                current[i] = current[swapAt];
                current[swapAt] = tmp;
                double cost = pathCost(current, d);
                if (cost < bestCost) {
                    bestCost = cost;
                    System.arraycopy(current, 0, best, 0, n);
                }
                c[i]++;
                i = 0;
            } else {
                c[i] = 0;
                i++;
            }
        }

        List<Integer> order = new ArrayList<>(n);
        for (int index : best) {
            order.add(index);
        }
        return new Result(order, bestCost, Strategy.BRUTE_FORCE);
    }

    /** 开环路径代价：起点(0) → 排列逐点，不回起点 */
    private static double pathCost(int[] perm, double[][] d) {
        double cost = d[0][perm[0] + 1];
        for (int i = 0; i < perm.length - 1; i++) {
            cost += d[perm[i] + 1][perm[i + 1] + 1];
        }
        return cost;
    }

    // ---------------- 最近邻 + 2-opt ----------------

    private static Result nearestNeighbor2Opt(int n, double[][] d) {
        // 最近邻构造初始解：从起点出发每次取最近未访问点
        int[] route = new int[n];
        boolean[] visited = new boolean[n];
        int cur = 0; // 矩阵中起点的下标
        for (int k = 0; k < n; k++) {
            int next = -1;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (!visited[i] && d[cur][i + 1] < best) {
                    best = d[cur][i + 1];
                    next = i;
                }
            }
            route[k] = next;
            visited[next] = true;
            cur = next + 1;
        }

        // 2-opt 迭代消除交叉（开环变体：j 可取到末尾，不引入回起点闭合边）
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = 0; i < n - 1; i++) {
                for (int j = i + 1; j < n; j++) {
                    // 反转 route[i..j]：断边 (i-1→i) 与 (j→j+1)，换边 (i-1→j) 与 (i→j+1)
                    double before = d[node(route, i - 1, 0)][route[i] + 1];
                    double after = d[node(route, i - 1, 0)][route[j] + 1];
                    if (j + 1 < n) {
                        before += d[route[j] + 1][route[j + 1] + 1];
                        after += d[route[i] + 1][route[j + 1] + 1];
                    }
                    if (after < before - 1e-9) {
                        reverse(route, i, j);
                        improved = true;
                    }
                }
            }
        }

        List<Integer> order = new ArrayList<>(n);
        for (int index : route) {
            order.add(index);
        }
        return new Result(order, pathCost(route, d), Strategy.NN_2OPT);
    }

    /** route[i-1] 越界（i==0）时取起点在矩阵中的下标 0 */
    private static int node(int[] route, int position, int startNode) {
        return position < 0 ? startNode : route[position] + 1;
    }

    private static void reverse(int[] route, int i, int j) {
        while (i < j) {
            int tmp = route[i];
            route[i] = route[j];
            route[j] = tmp;
            i++;
            j--;
        }
    }
}
