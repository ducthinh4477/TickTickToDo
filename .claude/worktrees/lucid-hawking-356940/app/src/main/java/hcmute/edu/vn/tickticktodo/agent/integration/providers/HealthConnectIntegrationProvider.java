package hcmute.edu.vn.doinbot.agent.integration.providers;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import hcmute.edu.vn.doinbot.agent.integration.IntegrationProvider;
import hcmute.edu.vn.doinbot.agent.integration.model.HealthSummary;

public class HealthConnectIntegrationProvider implements IntegrationProvider {

    private static final String HEALTH_CONNECT_SERVICE_NAME = "healthconnect";

    private static final String PERMISSION_READ_STEPS = "android.permission.health.READ_STEPS";
    private static final String PERMISSION_READ_SLEEP = "android.permission.health.READ_SLEEP";
    private static final String PERMISSION_READ_ACTIVE_CALORIES = "android.permission.health.READ_ACTIVE_CALORIES_BURNED";

    private static final String CLASS_READ_REQUEST = "android.health.connect.ReadRecordsRequest";
    private static final String CLASS_REQUEST_FILTER_BUILDER = "android.health.connect.ReadRecordsRequestUsingFilters$Builder";
    private static final String CLASS_TIME_FILTER = "android.health.connect.TimeRangeFilter";
    private static final String CLASS_TIME_INSTANT_FILTER_BUILDER = "android.health.connect.TimeInstantRangeFilter$Builder";
    private static final String CLASS_OUTCOME_RECEIVER = "android.os.OutcomeReceiver";

    private static final String CLASS_STEPS_RECORD = "android.health.connect.datatypes.StepsRecord";
    private static final String CLASS_SLEEP_RECORD = "android.health.connect.datatypes.SleepSessionRecord";
    private static final String CLASS_ACTIVE_CALORIES_RECORD = "android.health.connect.datatypes.ActiveCaloriesBurnedRecord";

    private static final long READ_TIMEOUT_MILLIS = 1800L;
    private static final int DEFAULT_PAGE_SIZE = 500;
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    private final Context appContext;

    public HealthConnectIntegrationProvider(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @Override
    public String getProviderName() {
        return "HEALTH_CONNECT";
    }

    @Override
    public HealthSummary getHealthSummary(long fromMillis, long toMillis) {
        if (!isHealthConnectSupported()) {
            return HealthSummary.unavailable(getProviderName());
        }

        Object manager = getHealthConnectManager();
        if (manager == null) {
            return HealthSummary.unavailable(getProviderName());
        }

        boolean canReadSteps = hasPermission(PERMISSION_READ_STEPS);
        boolean canReadSleep = hasPermission(PERMISSION_READ_SLEEP);
        boolean canReadActiveCalories = hasPermission(PERMISSION_READ_ACTIVE_CALORIES);

        if (!canReadSteps && !canReadSleep && !canReadActiveCalories) {
            return HealthSummary.unavailable(getProviderName());
        }

        try {
            long totalSteps = 0L;
            long totalSleepMillis = 0L;
            long totalActiveMillis = 0L;

            if (canReadSteps) {
                List<Object> steps = readRecords(manager, CLASS_STEPS_RECORD, fromMillis, toMillis, DEFAULT_PAGE_SIZE);
                for (Object record : steps) {
                    totalSteps += readLong(record, "getCount");
                }
            }

            if (canReadSleep) {
                List<Object> sleepSessions = readRecords(manager, CLASS_SLEEP_RECORD, fromMillis, toMillis, DEFAULT_PAGE_SIZE);
                for (Object record : sleepSessions) {
                    totalSleepMillis += getIntervalDurationMillis(record);
                }
            }

            if (canReadActiveCalories) {
                List<Object> activeCalories = readRecords(manager, CLASS_ACTIVE_CALORIES_RECORD, fromMillis, toMillis, DEFAULT_PAGE_SIZE);
                for (Object record : activeCalories) {
                    totalActiveMillis += getIntervalDurationMillis(record);
                }
            }

            float sleepHours = (float) (totalSleepMillis / 3600000.0d);
            int steps = safeLongToInt(totalSteps);
            int activeMinutes = safeLongToInt(totalActiveMillis / 60000L);

            String energy = inferEnergy(sleepHours, steps, activeMinutes);
            return new HealthSummary(true, sleepHours, steps, activeMinutes, energy, getProviderName());
        } catch (Exception ignored) {
            return HealthSummary.unavailable(getProviderName());
        }
    }

    private boolean isHealthConnectSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    }

    private Object getHealthConnectManager() {
        try {
            return appContext.getSystemService(HEALTH_CONNECT_SERVICE_NAME);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(appContext, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private List<Object> readRecords(Object manager,
                                     String recordClassName,
                                     long fromMillis,
                                     long toMillis,
                                     int pageSize) {
        try {
            Class<?> recordClass = Class.forName(recordClassName);
            Object request = buildReadRequest(recordClass, fromMillis, toMillis, pageSize);
            if (request == null) {
                return Collections.emptyList();
            }

            Class<?> requestClass = Class.forName(CLASS_READ_REQUEST);
            Class<?> outcomeReceiverClass = Class.forName(CLASS_OUTCOME_RECEIVER);

            Method readMethod = manager.getClass().getMethod(
                    "readRecords",
                    requestClass,
                    Executor.class,
                    outcomeReceiverClass
            );

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Object> responseRef = new AtomicReference<>(null);
            AtomicReference<Object> errorRef = new AtomicReference<>(null);

            InvocationHandler handler = (proxy, method, args) -> {
                String methodName = method.getName();
                if ("onResult".equals(methodName)) {
                    responseRef.set(args != null && args.length > 0 ? args[0] : null);
                    latch.countDown();
                } else if ("onError".equals(methodName)) {
                    errorRef.set(args != null && args.length > 0 ? args[0] : null);
                    latch.countDown();
                }
                return null;
            };

            Object outcomeReceiver = Proxy.newProxyInstance(
                    HealthConnectIntegrationProvider.class.getClassLoader(),
                    new Class<?>[]{outcomeReceiverClass},
                    handler
            );

            readMethod.invoke(manager, request, DIRECT_EXECUTOR, outcomeReceiver);

            boolean completed = latch.await(READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (!completed || errorRef.get() != null || responseRef.get() == null) {
                return Collections.emptyList();
            }

            Method getRecords = responseRef.get().getClass().getMethod("getRecords");
            Object recordsObject = getRecords.invoke(responseRef.get());
            if (recordsObject instanceof List<?>) {
                return new ArrayList<>((List<?>) recordsObject);
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
        }

        return Collections.emptyList();
    }

    private Object buildReadRequest(Class<?> recordClass,
                                    long fromMillis,
                                    long toMillis,
                                    int pageSize) {
        try {
            Class<?> requestBuilderClass = Class.forName(CLASS_REQUEST_FILTER_BUILDER);
            Constructor<?> builderConstructor = requestBuilderClass.getConstructor(Class.class);
            Object requestBuilder = builderConstructor.newInstance(recordClass);

            Object timeRangeFilter = buildTimeRangeFilter(fromMillis, toMillis);
            Class<?> timeRangeFilterClass = Class.forName(CLASS_TIME_FILTER);

            Method setTimeRangeFilter = requestBuilderClass.getMethod("setTimeRangeFilter", timeRangeFilterClass);
            Method setPageSize = requestBuilderClass.getMethod("setPageSize", int.class);
            Method build = requestBuilderClass.getMethod("build");

            setTimeRangeFilter.invoke(requestBuilder, timeRangeFilter);
            setPageSize.invoke(requestBuilder, Math.max(1, pageSize));

            return build.invoke(requestBuilder);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object buildTimeRangeFilter(long fromMillis, long toMillis) {
        try {
            Class<?> filterBuilderClass = Class.forName(CLASS_TIME_INSTANT_FILTER_BUILDER);
            Object filterBuilder = filterBuilderClass.getConstructor().newInstance();

            Method setStartTime = filterBuilderClass.getMethod("setStartTime", Instant.class);
            Method setEndTime = filterBuilderClass.getMethod("setEndTime", Instant.class);
            Method build = filterBuilderClass.getMethod("build");

            setStartTime.invoke(filterBuilder, Instant.ofEpochMilli(fromMillis));
            setEndTime.invoke(filterBuilder, Instant.ofEpochMilli(toMillis));
            return build.invoke(filterBuilder);
        } catch (Exception ignored) {
            return null;
        }
    }

    private long readLong(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private long getIntervalDurationMillis(Object intervalRecord) {
        try {
            Method getStartTime = intervalRecord.getClass().getMethod("getStartTime");
            Method getEndTime = intervalRecord.getClass().getMethod("getEndTime");
            Object startObject = getStartTime.invoke(intervalRecord);
            Object endObject = getEndTime.invoke(intervalRecord);
            if (!(startObject instanceof Instant) || !(endObject instanceof Instant)) {
                return 0L;
            }

            long duration = Duration.between((Instant) startObject, (Instant) endObject).toMillis();
            return Math.max(0L, duration);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private int safeLongToInt(long value) {
        if (value <= 0L) {
            return 0;
        }
        if (value >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }

    private String inferEnergy(float sleepHours, int steps, int activeMinutes) {
        if (sleepHours >= 7.0f && (steps >= 7000 || activeMinutes >= 45)) {
            return HealthSummary.ENERGY_HIGH;
        }
        if (sleepHours < 5.0f || (steps < 2000 && activeMinutes < 15)) {
            return HealthSummary.ENERGY_LOW;
        }
        return HealthSummary.ENERGY_MEDIUM;
    }
}
