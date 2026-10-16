package gcewing.sg.util;

public final class TimeFormat {

    private TimeFormat() {}

    // Ticks (at 20 a second) as the two largest units that apply, from months (of 30 days) down to seconds
    public static String runTime(double ticks) {
        long seconds = (long) (ticks / 20);
        long[] sizes = {30L * 24 * 3600, 7L * 24 * 3600, 24L * 3600, 3600, 60, 1};
        String[] units = {"mo", "w", "d", "h", "m", "s"};
        StringBuilder text = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < sizes.length && shown < 2; i++) {
            long n = seconds / sizes[i];
            if (n > 0 || shown > 0) {
                if (n > 0) {
                    text.append(text.length() > 0 ? " " : "").append(n).append(units[i]);
                }
                seconds -= n * sizes[i];
                shown++;
            }
        }
        return text.length() > 0 ? text.toString() : "0s";
    }
}
