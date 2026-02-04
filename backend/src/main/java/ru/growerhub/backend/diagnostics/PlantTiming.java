package ru.growerhub.backend.diagnostics;

import jakarta.persistence.EntityManagerFactory;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PlantTiming {
    private static final Logger log = LoggerFactory.getLogger(PlantTiming.class);
    private static final ThreadLocal<PlantTimingContext> CTX = new ThreadLocal<>();
    private static volatile boolean enabled;
    private static volatile Statistics statistics;

    public PlantTiming(
            @Value("${app.debug.plantsTiming:false}") boolean enabledFlag,
            ObjectProvider<EntityManagerFactory> emfProvider
    ) {
        enabled = enabledFlag;
        if (!enabled) {
            return;
        }
        EntityManagerFactory emf = emfProvider.getIfAvailable();
        if (emf == null) {
            return;
        }
        try {
            SessionFactory sessionFactory = emf.unwrap(SessionFactory.class);
            Statistics stats = sessionFactory.getStatistics();
            stats.setStatisticsEnabled(true);
            statistics = stats;
        } catch (Exception ignored) {
            statistics = null;
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isActive() {
        return enabled && CTX.get() != null;
    }

    public static void startRequest(String requestId) {
        if (!enabled) {
            return;
        }
        PlantTimingContext ctx = new PlantTimingContext(requestId);
        ctx.markSqlStart(statistics);
        CTX.set(ctx);
        if (requestId != null) {
            MDC.put("requestId", requestId);
        }
    }

    public static void finishRequest(int plantsCount, long totalNs) {
        PlantTimingContext ctx = CTX.get();
        if (!enabled || ctx == null) {
            return;
        }
        ctx.plantsCount = plantsCount;
        ctx.totalNs = totalNs;
        ctx.markSqlEnd(statistics);
        log.info(ctx.toLogLine());
        CTX.remove();
        MDC.remove("requestId");
    }

    public static long startTimer() {
        return isActive() ? System.nanoTime() : 0L;
    }

    public static void recordPlantList(long startNs) {
        PlantTimingContext ctx = CTX.get();
        if (!isActive() || startNs == 0L) {
            return;
        }
        ctx.plantListNs += elapsedNs(startNs);
    }

    public static void recordSensors(Integer plantId, long startNs) {
        PlantTimingContext ctx = CTX.get();
        if (!isActive() || startNs == 0L) {
            return;
        }
        ctx.addSensors(plantId, elapsedNs(startNs));
    }

    public static void recordPumps(Integer plantId, long startNs) {
        PlantTimingContext ctx = CTX.get();
        if (!isActive() || startNs == 0L) {
            return;
        }
        ctx.addPumps(plantId, elapsedNs(startNs));
    }

    public static void recordAdvice(Integer plantId, long startNs) {
        PlantTimingContext ctx = CTX.get();
        if (!isActive() || startNs == 0L) {
            return;
        }
        ctx.addAdvice(plantId, elapsedNs(startNs));
    }

    public static void recordJournal(long startNs) {
        PlantTimingContext ctx = CTX.get();
        if (!isActive() || startNs == 0L) {
            return;
        }
        ctx.journalNs += elapsedNs(startNs);
    }

    public static void recordHistory(long startNs) {
        PlantTimingContext ctx = CTX.get();
        if (!isActive() || startNs == 0L) {
            return;
        }
        ctx.historyNs += elapsedNs(startNs);
    }

    public static void recordShadowHit() {
        PlantTimingContext ctx = CTX.get();
        if (!isActive()) {
            return;
        }
        ctx.shadowHitCount += 1;
    }

    public static void recordShadowMiss() {
        PlantTimingContext ctx = CTX.get();
        if (!isActive()) {
            return;
        }
        ctx.shadowMissCount += 1;
    }

    public static void recordShadowLoad(long startNs) {
        PlantTimingContext ctx = CTX.get();
        if (!isActive() || startNs == 0L) {
            return;
        }
        ctx.shadowLoadNs += elapsedNs(startNs);
    }

    public static void recordShadowJson(long startNs) {
        PlantTimingContext ctx = CTX.get();
        if (!isActive() || startNs == 0L) {
            return;
        }
        ctx.shadowJsonNs += elapsedNs(startNs);
    }

    private static long elapsedNs(long startNs) {
        return System.nanoTime() - startNs;
    }

    private static final class PlantTimingContext {
        private final String requestId;
        private long totalNs;
        private int plantsCount;
        private long plantListNs;
        private long sensorsNs;
        private long pumpsNs;
        private long adviceNs;
        private long journalNs;
        private long historyNs;
        private long shadowHitCount;
        private long shadowMissCount;
        private long shadowLoadNs;
        private long shadowJsonNs;
        private long sqlCount = -1;
        private long sqlTotalMs = -1;
        private long sqlCountStart = -1;
        private boolean sqlEnabled;
        private long sensorsMaxNs;
        private Integer sensorsMaxPlantId;
        private long pumpsMaxNs;
        private Integer pumpsMaxPlantId;
        private long adviceMaxNs;
        private Integer adviceMaxPlantId;

        private PlantTimingContext(String requestId) {
            this.requestId = requestId;
        }

        private void markSqlStart(Statistics stats) {
            if (stats == null || !stats.isStatisticsEnabled()) {
                return;
            }
            sqlEnabled = true;
            sqlCountStart = stats.getPrepareStatementCount();
        }

        private void markSqlEnd(Statistics stats) {
            if (!sqlEnabled || stats == null) {
                return;
            }
            sqlCount = stats.getPrepareStatementCount() - sqlCountStart;
            sqlTotalMs = -1;
        }

        private void addSensors(Integer plantId, long durationNs) {
            sensorsNs += durationNs;
            if (durationNs > sensorsMaxNs) {
                sensorsMaxNs = durationNs;
                sensorsMaxPlantId = plantId;
            }
        }

        private void addPumps(Integer plantId, long durationNs) {
            pumpsNs += durationNs;
            if (durationNs > pumpsMaxNs) {
                pumpsMaxNs = durationNs;
                pumpsMaxPlantId = plantId;
            }
        }

        private void addAdvice(Integer plantId, long durationNs) {
            adviceNs += durationNs;
            if (durationNs > adviceMaxNs) {
                adviceMaxNs = durationNs;
                adviceMaxPlantId = plantId;
            }
        }

        private String toLogLine() {
            StringBuilder sb = new StringBuilder(256);
            sb.append("plantsTiming");
            sb.append(" requestId=").append(requestId != null ? requestId : "-");
            sb.append(" totalMs=").append(nsToMs(totalNs));
            sb.append(" plantsCount=").append(plantsCount);
            sb.append(" sqlCount=").append(sqlCount);
            sb.append(" sqlTotalMs=").append(sqlTotalMs);
            sb.append(" shadowHitCount=").append(shadowHitCount);
            sb.append(" shadowMissCount=").append(shadowMissCount);
            sb.append(" shadowLoadMs=").append(nsToMs(shadowLoadNs));
            sb.append(" shadowJsonMs=").append(nsToMs(shadowJsonNs));
            sb.append(" plantListMs=").append(nsToMs(plantListNs));
            sb.append(" sensorsMs=").append(nsToMs(sensorsNs));
            sb.append(" sensorsMaxMs=").append(nsToMs(sensorsMaxNs));
            sb.append(" sensorsMaxPlantId=").append(sensorsMaxPlantId != null ? sensorsMaxPlantId : -1);
            sb.append(" pumpsMs=").append(nsToMs(pumpsNs));
            sb.append(" pumpsMaxMs=").append(nsToMs(pumpsMaxNs));
            sb.append(" pumpsMaxPlantId=").append(pumpsMaxPlantId != null ? pumpsMaxPlantId : -1);
            sb.append(" adviceMs=").append(nsToMs(adviceNs));
            sb.append(" adviceMaxMs=").append(nsToMs(adviceMaxNs));
            sb.append(" adviceMaxPlantId=").append(adviceMaxPlantId != null ? adviceMaxPlantId : -1);
            sb.append(" journalMs=").append(nsToMs(journalNs));
            sb.append(" historyMs=").append(nsToMs(historyNs));
            return sb.toString();
        }

        private long nsToMs(long ns) {
            if (ns <= 0L) {
                return 0L;
            }
            return TimeUnit.NANOSECONDS.toMillis(ns);
        }
    }
}
