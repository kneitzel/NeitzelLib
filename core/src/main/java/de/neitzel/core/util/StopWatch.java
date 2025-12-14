package de.neitzel.core.util;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * A utility class for measuring elapsed time. The StopWatch can be started, stopped, and queried for the elapsed time in various formats.
 */
@SuppressWarnings("unused")
public class StopWatch {

    /**
     * Represents the starting time of the stopwatch. This variable is set when the stopwatch is started and is used to calculate the elapsed
     * duration between the start and the stop or the current time.
     */
    private Instant startTime;

    /**
     * Represents the stopping time of the stopwatch. This variable is set when the stopwatch is stopped and is used to calculate the elapsed
     * duration between the start and stop times.
     */
    private Instant stopTime;

    /**
     * Creates a new StopWatch instance in the reset state.
     *
     * <p>The constructor does not start timing. Both {@link #startTime} and {@link #stopTime}
     * are initialized to {@code null}. To begin measuring elapsed time, call {@link #start()}.
     * After calling {@link #stop()}, retrieve the measured duration with {@link #getUsedTime()} or
     * a human-readable string via {@link #getUsedTimeFormatted(boolean, boolean, boolean, boolean, boolean)}.
     * </p>
     *
     * <p>Note: This class is not synchronized; synchronize externally if the same instance
     * is accessed from multiple threads.</p>
     */
    public StopWatch() {
        // default constructor only ...
    }

    /**
     * Starts the stopwatch by recording the current time as the starting time. If the stopwatch was previously stopped or not yet started,
     * this method resets the starting time to the current time and clears the stopping time. This method is typically called before measuring
     * elapsed time.
     */
    public void start() {
        this.startTime = Instant.now();
        this.stopTime = null;
    }

    /**
     * Stops the stopwatch by recording the current time as the stopping time.
     * <p>
     * This method should only be called after the stopwatch has been started using the {@code start()} method. If the stopwatch has not been
     * started, an {@code IllegalStateException} will be thrown. Once this method is called, the elapsed time can be calculated as the
     * duration between the starting and stopping times.
     *
     * @throws IllegalStateException if the stopwatch has not been started prior to calling this method.
     */
    public void stop() {
        if (startTime == null) {
            throw new IllegalStateException("StopWatch must be started before stopping.");
        }
        this.stopTime = Instant.now();
    }

    /**
     * Provides a compact and human-readable formatted string representing the elapsed time measured by the stopwatch. The method formats the
     * duration using a combination of days, hours, minutes, seconds, and milliseconds. This compact format removes additional verbosity in
     * favor of a shorter, more concise representation.
     *
     * @return a compact formatted string representing the elapsed time, including days, hours, minutes, seconds, and milliseconds as
     * applicable
     */
    public String getUsedTimeFormattedCompact() {
        return getUsedTimeFormatted(true, true, true, true, true);
    }

    /**
     * Formats the duration of elapsed time measured by the stopwatch into a human-readable string. The formatted output may include days,
     * hours, minutes, seconds, and milliseconds, depending on the parameters. The method retrieves the elapsed time via the
     * {@code getUsedTime} method and formats it based on the specified flags.
     *
     * @param includeMillis  whether to include milliseconds in the formatted output
     * @param includeSeconds whether to include seconds in the formatted output
     * @param includeMinutes whether to include minutes in the formatted output
     * @param includeHours   whether to include hours in the formatted output
     * @param includeDays    whether to include days in the formatted output
     * @return a formatted string representing the elapsed time, including the components specified by the parameters
     */
    public String getUsedTimeFormatted(boolean includeMillis, boolean includeSeconds, boolean includeMinutes, boolean includeHours,
                                       boolean includeDays) {
        Duration duration = getUsedTime();

        long millis = duration.toMillis();
        long days = millis / (24 * 60 * 60 * 1000);
        millis %= (24 * 60 * 60 * 1000);

        long hours = millis / (60 * 60 * 1000);
        millis %= (60 * 60 * 1000);
        long minutes = millis / (60 * 1000);
        millis %= (60 * 1000);
        long seconds = millis / 1000;
        millis %= 1000;

        StringBuilder sb = new StringBuilder();

        if (includeDays && days > 0) {
            sb.append(days).append("d ");
        }
        if (includeHours && (hours > 0 || !sb.isEmpty())) {
            sb.append(hours).append("h ");
        }
        if (includeMinutes && (minutes > 0 || !sb.isEmpty())) {
            sb.append(minutes).append("m ");
        }
        if (includeSeconds && (seconds > 0 || !sb.isEmpty())) {
            sb.append(seconds);
        }
        if (includeMillis) {
            if (includeSeconds) {
                sb.append(String.format(Locale.US, ".%03d", millis));
            } else {
                sb.append(millis).append("ms");
            }
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? "0ms" : result;
    }

    /**
     * Calculates the amount of time elapsed between the stopwatch's starting time and either its stopping time or the current time if the
     * stopwatch is still running.
     *
     * @return the duration representing the elapsed time between the start and end points of the stopwatch.
     * @throws IllegalStateException if the stopwatch has not been started before calling this method.
     */
    public Duration getUsedTime() {
        if (startTime == null) {
            throw new IllegalStateException("StopWatch has not been started.");
        }
        Instant end = (stopTime != null) ? stopTime : Instant.now();
        return Duration.between(startTime, end);
    }
}
