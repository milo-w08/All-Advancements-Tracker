package dev.milow.aaigttracker.client;

import dev.milow.aaigttracker.AllAdvancementsIgtTracker;
import java.util.HashMap;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

public final class SpeedRunIgtBridge {
    private static final List<String> TIMER_CLASS_NAMES = List.of(
            "com.redlimerl.speedrunigt.timer.InGameTimer",
            "com.redlimerl.speedrunigt.InGameTimer"
    );

    private static final List<String> MILLIS_METHOD_NAMES = List.of(
            "getInGameTime",
            "getTime",
            "getTotalTime"
    );

    private static final List<String> VALUE_METHOD_NAMES = List.of(
            "toMillis",
            "toMilliseconds",
            "getMillis",
            "getMilliseconds",
            "asMillis"
    );

    private Method getInstanceMethod;
    private Method millisMethod;
    private Method getAdvancementsTrackerMethod;
    private Method getAdvancementsMethod;
    private Method isCompleteMethod;
    private Method isAdvancementMethod;
    private Method getIgtMethod;
    private boolean initialized;

    public long readMillis() {
        if (!FabricLoader.getInstance().isModLoaded("speedrunigt")) {
            return -1L;
        }

        if (!this.initialized) {
            this.initialize();
        }

        if (this.getInstanceMethod == null || this.millisMethod == null) {
            return -1L;
        }

        try {
            Object timer = this.readTimerInstance();
            if (timer == null) {
                return -1L;
            }

            Object value = this.millisMethod.invoke(timer);
            Long millis = this.coerceToMillis(value);
            return millis != null ? millis : -1L;
        } catch (ReflectiveOperationException exception) {
            AllAdvancementsIgtTracker.LOGGER.debug("Failed to read SpeedRunIGT timer via reflection", exception);
            return -1L;
        }
    }

    public Map<Identifier, TrackedAdvancement> readTrackedAdvancements() {
        if (!FabricLoader.getInstance().isModLoaded("speedrunigt")) {
            return Map.of();
        }

        if (!this.initialized) {
            this.initialize();
        }

        if (this.getInstanceMethod == null
                || this.getAdvancementsTrackerMethod == null
                || this.getAdvancementsMethod == null
                || this.isCompleteMethod == null
                || this.isAdvancementMethod == null
                || this.getIgtMethod == null) {
            return Map.of();
        }

        try {
            Object timer = this.readTimerInstance();
            if (timer == null) {
                return Map.of();
            }

            Object tracker = this.getAdvancementsTrackerMethod.invoke(timer);
            if (tracker == null) {
                return Map.of();
            }

            Object value = this.getAdvancementsMethod.invoke(tracker);
            if (!(value instanceof Map<?, ?> advancementMap)) {
                return Map.of();
            }

            Map<Identifier, TrackedAdvancement> trackedAdvancements = new HashMap<>();
            for (Map.Entry<?, ?> entry : advancementMap.entrySet()) {
                if (!(entry.getKey() instanceof String rawId) || entry.getValue() == null) {
                    continue;
                }

                Identifier id;
                try {
                    id = Identifier.parse(rawId);
                } catch (RuntimeException exception) {
                    continue;
                }

                Object track = entry.getValue();
                boolean complete = Boolean.TRUE.equals(this.isCompleteMethod.invoke(track));
                boolean advancement = Boolean.TRUE.equals(this.isAdvancementMethod.invoke(track));
                Long igtMillis = complete ? this.coerceToMillis(this.getIgtMethod.invoke(track)) : null;
                trackedAdvancements.put(id, new TrackedAdvancement(advancement, complete, igtMillis));
            }

            return Map.copyOf(trackedAdvancements);
        } catch (ReflectiveOperationException exception) {
            AllAdvancementsIgtTracker.LOGGER.debug("Failed to read SpeedRunIGT advancement tracker via reflection", exception);
            return Map.of();
        }
    }

    private void initialize() {
        this.initialized = true;

        for (String className : TIMER_CLASS_NAMES) {
            try {
                Class<?> timerClass = Class.forName(className);
                Method instance = timerClass.getMethod("getInstance");
                Method millis = resolveFirstMethod(timerClass, MILLIS_METHOD_NAMES);

                this.getInstanceMethod = instance;
                this.millisMethod = millis;
                this.getAdvancementsTrackerMethod = resolveMethod(timerClass, "getAdvancementsTracker");
                this.resolveAdvancementTrackerApi();

                if (this.millisMethod != null || this.getAdvancementsTrackerMethod != null) {
                    return;
                }
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            }
        }

        AllAdvancementsIgtTracker.LOGGER.info("SpeedRunIGT detected, but its timer API could not be resolved. Falling back to level time.");
    }

    private void resolveAdvancementTrackerApi() {
        if (this.getAdvancementsTrackerMethod == null) {
            return;
        }

        try {
            Class<?> trackerClass = Class.forName("com.redlimerl.speedrunigt.timer.TimerAdvancementTracker");
            Class<?> advancementTrackClass = Class.forName("com.redlimerl.speedrunigt.timer.TimerAdvancementTracker$AdvancementTrack");
            this.getAdvancementsMethod = trackerClass.getMethod("getAdvancements");
            this.isCompleteMethod = advancementTrackClass.getMethod("isComplete");
            this.isAdvancementMethod = advancementTrackClass.getMethod("isAdvancement");
            this.getIgtMethod = advancementTrackClass.getMethod("getIGT");
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            AllAdvancementsIgtTracker.LOGGER.debug("SpeedRunIGT advancement tracker API could not be resolved", exception);
        }
    }

    private Object readTimerInstance() throws ReflectiveOperationException {
        return this.getInstanceMethod.invoke(null);
    }

    private static Method resolveFirstMethod(Class<?> type, List<String> methodNames) {
        for (String methodName : methodNames) {
            Method method = resolveMethod(type, methodName);
            if (method != null) {
                return method;
            }
        }

        return null;
    }

    private static Method resolveMethod(Class<?> type, String methodName) {
        try {
            return type.getMethod(methodName);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private Long coerceToMillis(Object value) {
        if (value instanceof Number number) {
            return Long.valueOf(number.longValue());
        }

        if (value == null) {
            return null;
        }

        for (String methodName : VALUE_METHOD_NAMES) {
            try {
                Method method = value.getClass().getMethod(methodName);
                Object nestedValue = method.invoke(value);
                if (nestedValue instanceof Number number) {
                    return Long.valueOf(number.longValue());
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return null;
    }

    public record TrackedAdvancement(boolean isAdvancement, boolean complete, Long igtMillis) {
        public boolean hasKnownIgt() {
            return this.igtMillis != null;
        }
    }
}
