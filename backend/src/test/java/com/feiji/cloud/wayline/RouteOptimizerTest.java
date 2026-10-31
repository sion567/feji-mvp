package com.feiji.cloud.wayline;

import com.feiji.cloud.wayline.RouteOptimizer.Point;
import com.feiji.cloud.wayline.RouteOptimizer.Result;
import com.feiji.cloud.wayline.RouteOptimizer.Strategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteOptimizerTest {

    private static final double TOLERANCE_M = 1.0;

    @Test
    void shouldReturnEmptyForNoPoints() {
        Result result = RouteOptimizer.optimize(new Point(31, 121, 0), List.of());
        assertTrue(result.order().isEmpty());
        assertEquals(0, result.totalDistanceMeters(), TOLERANCE_M);
    }

    @Test
    void shouldReturnSinglePoint() {
        Point start = new Point(31.0, 121.0, 0);
        Result result = RouteOptimizer.optimize(start, List.of(new Point(31.01, 121.0, 0)));
        assertEquals(List.of(0), result.order());
        assertEquals(RouteOptimizer.haversineMeters(31.0, 121.0, 31.01, 121.0),
                result.totalDistanceMeters(), TOLERANCE_M);
    }

    /** 共线点：最优序即按距离依次经过，任何回头路都更长 */
    @Test
    void shouldOrderCollinearPointsByDistance() {
        Point start = new Point(31.0, 121.0, 0);
        // 南北向共线：+3km、+1km、+2km（纬度 1 分 ≈ 1.852km）
        List<Point> points = List.of(
                new Point(31.0 + 3.0 / 1.852 / 60, 121.0, 0),
                new Point(31.0 + 1.0 / 1.852 / 60, 121.0, 0),
                new Point(31.0 + 2.0 / 1.852 / 60, 121.0, 0));
        Result result = RouteOptimizer.optimize(start, points);
        assertEquals(List.of(1, 2, 0), result.order());
    }

    /** 矩形四角、起点占一角：暴力法应唯一最优序 = 沿短边出发绕两邻边（唯一无交叉序） */
    @Test
    void bruteForceShouldFindOptimalTour() {
        // 起点在矩形西南角，三个点为其余三角（纬度、经度，单位度）
        Point start = new Point(0.0, 0.0, 0);
        List<Point> points = List.of(
                new Point(4.0, 3.0, 0),  // 东北角（对角）
                new Point(4.0, 0.0, 0),  // 东南角（与起点同经线）
                new Point(0.0, 3.0, 0)); // 西北角（与起点同纬线）
        Result result = RouteOptimizer.optimize(start, points);
        assertEquals(Strategy.BRUTE_FORCE, result.strategy());
        // 唯一最优：先西北（短边）→ 东北（长边）→ 东南（短边）；反向序因纬线弧长随纬度变化而更长
        assertEquals(List.of(2, 0, 1), result.order());
        double expected = RouteOptimizer.distance(start, points.get(2))
                + RouteOptimizer.distance(points.get(2), points.get(0))
                + RouteOptimizer.distance(points.get(0), points.get(1));
        assertEquals(expected, result.totalDistanceMeters(), expected * 1e-6);
    }

    /** 2-opt 相对纯最近邻：永不劣化，且随机布局中必然存在严格改进（消除 NN 交叉） */
    @Test
    void twoOptShouldImproveNearestNeighbor() {
        Random random = new Random(2026);
        boolean foundStrictImprovement = false;
        for (int trial = 0; trial < 30; trial++) {
            Point start = new Point(31.0, 121.0, 0);
            List<Point> points = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                points.add(new Point(31.0 + random.nextDouble() * 0.1,
                        121.0 + random.nextDouble() * 0.1, 0));
            }
            double nn = nearestNeighborOnly(start, points).totalDistanceMeters();
            double optimized = RouteOptimizer.optimize(start, points, Strategy.NN_2OPT)
                    .totalDistanceMeters();
            assertTrue(optimized <= nn + TOLERANCE_M, "2-opt 不应劣于最近邻");
            foundStrictImprovement |= optimized < nn - TOLERANCE_M;
        }
        assertTrue(foundStrictImprovement, "随机布局中应存在 2-opt 严格改进最近邻的情形");
    }

    /** 大于暴力上限走 NN+2-opt，结果不劣于纯最近邻，且点集不变 */
    @Test
    void nn2OptShouldNotBeWorseThanNearestNeighborAndPreservePoints() {
        Random random = new Random(42);
        Point start = new Point(31.0, 121.0, 0);
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            points.add(new Point(31.0 + random.nextDouble() * 0.1,
                    121.0 + random.nextDouble() * 0.1, 0));
        }

        Result result = RouteOptimizer.optimize(start, points);
        assertEquals(Strategy.NN_2OPT, result.strategy());
        assertTrue(nearestNeighborOnly(start, points).totalDistanceMeters()
                >= result.totalDistanceMeters() - TOLERANCE_M);

        // 仅顺序变化：点集不变（不丢点不重点）
        assertEquals(points.size(), result.order().size());
        boolean[] seen = new boolean[points.size()];
        for (int index : result.order()) {
            assertTrue(index >= 0 && index < points.size() && !seen[index]);
            seen[index] = true;
        }
    }

    /** 强制 NN+2-opt 与暴力对照：小规模随机布局下 2-opt 应接近全局最优 */
    @Test
    void nn2OptShouldApproachBruteForceQuality() {
        Random random = new Random(7);
        for (int trial = 0; trial < 10; trial++) {
            Point start = new Point(30 + random.nextDouble(), 120 + random.nextDouble(), 0);
            List<Point> points = new ArrayList<>();
            int n = 8 + random.nextInt(3); // 8~10 点，暴力参照可枚举
            for (int i = 0; i < n; i++) {
                points.add(new Point(30 + random.nextDouble() * 0.2,
                        120 + random.nextDouble() * 0.2, 0));
            }
            double bruteBest = RouteOptimizer.optimize(start, points, Strategy.BRUTE_FORCE)
                    .totalDistanceMeters();
            double nn2opt = RouteOptimizer.optimize(start, points, Strategy.NN_2OPT)
                    .totalDistanceMeters();
            // 经验上 2-opt 可达最优 5% 以内，放宽到 15% 防随机布局的极端小样本
            assertTrue(nn2opt <= bruteBest * 1.15,
                    "trial=" + trial + " nn2opt=" + nn2opt + " brute=" + bruteBest);
        }
    }

    /** 已知城市对：北京—上海 ≈ 1068km */
    @Test
    void haversineShouldMatchKnownDistance() {
        double d = RouteOptimizer.haversineMeters(39.9042, 116.4074, 31.2304, 121.4737);
        assertEquals(1_068_000, d, 5_000);
    }

    /** 高度差参与 3D 距离：同经纬度不同高度，距离 = |Δh| */
    @Test
    void heightDifferenceShouldContributeToDistance() {
        assertEquals(100, RouteOptimizer.distance(
                new Point(31, 121, 0), new Point(31, 121, 100)), TOLERANCE_M);
        // 高度未知（NaN）按 0 高差
        assertEquals(0, RouteOptimizer.distance(
                new Point(31, 121, Double.NaN), new Point(31, 121, 500)), TOLERANCE_M);
    }

    // ---------------- 辅助：纯最近邻（无 2-opt）参照实现 ----------------

    private static Result nearestNeighborOnly(Point start, List<Point> points) {
        int n = points.size();
        double[][] d = new double[n + 1][n + 1];
        for (int i = 1; i <= n; i++) {
            d[0][i] = d[i][0] = RouteOptimizer.distance(start, points.get(i - 1));
            for (int j = 1; j <= n; j++) {
                d[i][j] = RouteOptimizer.distance(points.get(i - 1), points.get(j - 1));
            }
        }
        int[] route = new int[n];
        boolean[] visited = new boolean[n];
        int cur = 0;
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
        double cost = d[0][route[0] + 1];
        for (int i = 0; i < n - 1; i++) {
            cost += d[route[i] + 1][route[i + 1] + 1];
        }
        List<Integer> order = new ArrayList<>();
        for (int index : route) {
            order.add(index);
        }
        return new Result(order, cost, Strategy.NN_2OPT);
    }
}
