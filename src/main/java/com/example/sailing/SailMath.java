package com.example.sailing;

/** Sailing maths shared by the server (stress, ideal trim) and the client (the actual forces). */
public final class SailMath {
    private SailMath() {}

    /** Relative boat speed by angle between the bow and the wind source (0 = straight into it). */
    private static final double[][] POLAR = {
            {0, 0.0}, {28, 0.0}, {40, 0.35}, {52, 0.70}, {75, 0.95}, {100, 1.0},
            {125, 1.08}, {150, 0.90}, {170, 0.72}, {180, 0.65}
    };

    /** Best sail angle (degrees from the centreline) by the same wind angle. */
    private static final double[][] OPT = {
            {0, 5}, {30, 6}, {45, 12}, {90, 40}, {135, 65}, {180, 88}
    };

    private static double interp(double[][] table, double x) {
        if (x <= table[0][0]) return table[0][1];
        for (int i = 1; i < table.length; i++) {
            if (x <= table[i][0]) {
                double t = (x - table[i - 1][0]) / (table[i][0] - table[i - 1][0]);
                return table[i - 1][1] + t * (table[i][1] - table[i - 1][1]);
            }
        }
        return table[table.length - 1][1];
    }

    public static double polar(double absWindAngle) {
        return interp(POLAR, absWindAngle);
    }

    public static double optTrim(double absWindAngle) {
        return interp(OPT, absWindAngle);
    }

    /**
     * Sea surface height at a point. Two crossing swells that travel downwind.
     * windFrom is a Minecraft yaw (the direction you face to look into the wind).
     */
    public static double waveHeight(double x, double z, double ticks, double windFrom, double amp) {
        double a = Math.toRadians(windFrom);
        double dx = Math.sin(a), dz = -Math.cos(a);            // direction the wind blows toward
        double b = a + Math.toRadians(25);
        double ex = Math.sin(b), ez = -Math.cos(b);
        double p1 = 0.35 * (dx * x + dz * z) - 0.12 * ticks;
        double p2 = 0.80 * (ex * x + ez * z) - 0.20 * ticks + 1.3;
        return amp * (Math.sin(p1) + 0.5 * Math.sin(p2));
    }
}
