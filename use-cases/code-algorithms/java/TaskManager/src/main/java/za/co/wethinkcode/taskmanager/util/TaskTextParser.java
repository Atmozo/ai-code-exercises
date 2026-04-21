package za.co.wethinkcode.taskmanager.util;

import za.co.wethinkcode.taskmanager.model.Task;
import za.co.wethinkcode.taskmanager.model.TaskPriority;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TaskTextParser {

    private static final Pattern PRIORITY_PATTERN =
            Pattern.compile("(^|\\s)!([1-4]|urgent|high|medium|low)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern TAG_PATTERN = Pattern.compile("\\s@(\\w+)");

    private static final Pattern DATE_PATTERN = Pattern.compile("\\s#(\\w+)");

    /**
     * Parse free-form text to extract task properties.
     *
     * <p>Examples: "Buy milk @shopping !2 #tomorrow" "Finish report !urgent #friday @work"
     */
    public static Task parseTaskFromText(String text) {

        String title = text.trim();
        TaskPriority priority = TaskPriority.MEDIUM;
        LocalDateTime dueDate = null;
        List<String> tags = new ArrayList<>();

        // ---------- PRIORITY ----------
        Matcher priorityMatcher = PRIORITY_PATTERN.matcher(title);

        if (priorityMatcher.find()) {
            String priorityText = priorityMatcher.group(2).toLowerCase();

            // keep captured whitespace/start marker
            title = PRIORITY_PATTERN.matcher(title).replaceAll("$1");

            switch (priorityText) {
                case "1":
                case "low":
                    priority = TaskPriority.LOW;
                    break;

                case "2":
                case "medium":
                    priority = TaskPriority.MEDIUM;
                    break;

                case "3":
                case "high":
                    priority = TaskPriority.HIGH;
                    break;

                case "4":
                case "urgent":
                    priority = TaskPriority.URGENT;
                    break;

                default:
                    break;
            }
        }

        // remove invalid numeric priorities like !5
        title = title.replaceAll("(^|\\s)!\\d+\\b", "$1");

        // ---------- TAGS ----------
        Matcher tagMatcher = TAG_PATTERN.matcher(text);

        while (tagMatcher.find()) {
            tags.add(tagMatcher.group(1));
        }

        title = title.replaceAll("\\s@\\w+", "");

        // ---------- DATES ----------
        Matcher dateMatcher = DATE_PATTERN.matcher(text);
        List<String> dates = new ArrayList<>();

        while (dateMatcher.find()) {
            dates.add(dateMatcher.group(1));
        }

        title = title.replaceAll("\\s#(\\w+)", "");

        if (!dates.isEmpty()) {

            LocalDateTime today =
                    LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);

            for (String dateStr : dates) {

                String lowerDateStr = dateStr.toLowerCase();

                if (lowerDateStr.equals("today") || lowerDateStr.equals("now")) {

                    dueDate = today;
                    break;

                } else if (lowerDateStr.equals("tomorrow")) {

                    dueDate = today.plusDays(1);
                    break;

                } else if (lowerDateStr.equals("next_week") || lowerDateStr.equals("nextweek")) {

                    dueDate = today.plusDays(7);
                    break;

                } else if (Arrays.asList(
                                "monday", "mon",
                                "tuesday", "tue",
                                "wednesday", "wed",
                                "thursday", "thu",
                                "friday", "fri",
                                "saturday", "sat",
                                "sunday", "sun")
                        .contains(lowerDateStr)) {

                    Map<String, Integer> dayMap =
                            Map.ofEntries(
                                    Map.entry("monday", DayOfWeek.MONDAY.getValue()),
                                    Map.entry("mon", DayOfWeek.MONDAY.getValue()),
                                    Map.entry("tuesday", DayOfWeek.TUESDAY.getValue()),
                                    Map.entry("tue", DayOfWeek.TUESDAY.getValue()),
                                    Map.entry("wednesday", DayOfWeek.WEDNESDAY.getValue()),
                                    Map.entry("wed", DayOfWeek.WEDNESDAY.getValue()),
                                    Map.entry("thursday", DayOfWeek.THURSDAY.getValue()),
                                    Map.entry("thu", DayOfWeek.THURSDAY.getValue()),
                                    Map.entry("friday", DayOfWeek.FRIDAY.getValue()),
                                    Map.entry("fri", DayOfWeek.FRIDAY.getValue()),
                                    Map.entry("saturday", DayOfWeek.SATURDAY.getValue()),
                                    Map.entry("sat", DayOfWeek.SATURDAY.getValue()),
                                    Map.entry("sunday", DayOfWeek.SUNDAY.getValue()),
                                    Map.entry("sun", DayOfWeek.SUNDAY.getValue()));

                    dueDate = getNextWeekday(today, dayMap.get(lowerDateStr));
                    break;
                }

                try {
                    dueDate = LocalDate.parse(lowerDateStr).atStartOfDay();
                    break;

                } catch (DateTimeParseException e) {
                    // ignore invalid token, continue
                }
            }

            if (dueDate == null) {
                // Known silent drop:
                // date token removed from title but not understood.
            }
        }

        // ---------- CLEANUP ----------
        title = title.replaceAll("\\s+", " ").trim();

        Task task = new Task(title);
        task.setPriority(priority);
        task.setDueDate(dueDate);
        task.setTags(tags);

        return task;
    }

    /**
     * Returns next future occurrence of target weekday. If already that weekday, returns next
     * week's occurrence.
     */
    private static LocalDateTime getNextWeekday(LocalDateTime date, int targetDayOfWeek) {

        int currentDay = date.getDayOfWeek().getValue();

        int daysToAdd = (targetDayOfWeek - currentDay + 7) % 7;

        if (daysToAdd == 0) {
            daysToAdd = 7;
        }

        return date.plusDays(daysToAdd);
    }
}
