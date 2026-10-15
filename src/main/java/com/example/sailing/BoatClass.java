package com.example.sailing;

/** The three dinghy classes a rigging kit can turn a boat into. */
public enum BoatClass {
    //            name         top speed  gust limit (knots before it gets tippy)  default sail
    OPTIMIST("Optimist", 0.27, 26.0, "white"),
    LASER("Laser", 0.37, 19.0, "white"),
    SKIFF("Skiff", 0.47, 15.0, "red");

    public final String label;
    public final double vmax;
    public final double gustLimit;
    public final String defaultSail;

    BoatClass(String label, double vmax, double gustLimit, String defaultSail) {
        this.label = label;
        this.vmax = vmax;
        this.gustLimit = gustLimit;
        this.defaultSail = defaultSail;
    }

    public static BoatClass of(int i) {
        BoatClass[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
