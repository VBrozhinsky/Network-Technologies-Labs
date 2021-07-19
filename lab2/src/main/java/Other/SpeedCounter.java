package Other;

public class SpeedCounter {
    private static volatile long lastBytesCount = 0;
    private static volatile long startTime = 0;
    private static volatile long lastTime = 0;
    private static volatile long endTime = 0;
    private static volatile long instantSpeed = 0;


    public SpeedCounter() { }

    public static void addBytesCount(long gottenBytesCount) {
        long newTime = System.currentTimeMillis();
        if((newTime - lastTime) == 0) {
            instantSpeed = 0;
        } else {
            instantSpeed = (gottenBytesCount) / (newTime - lastTime);
        }
        lastBytesCount += gottenBytesCount;
        lastTime = newTime;
    }

    public static void setEndTime(long gottenLastTime) {
        endTime = gottenLastTime;
    }

    public double getInstantSpeed() {
        return (double) instantSpeed * 1000 / 1024 / 1024;
    }

    public double getAverageSpeed() {
        return (double) lastBytesCount / (System.currentTimeMillis() - startTime) * 1000 / 1024 / 1024;
    }

    public static void setStartTime(long gottenFirstTime) {
        startTime = lastTime = gottenFirstTime;
    }

    public void reset() {
        lastBytesCount = 0;
        startTime = 0;
        endTime = 0;
        instantSpeed = 0;
    }
}
