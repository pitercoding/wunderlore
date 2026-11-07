package com.wunderlore.game.whereinmiddleearth;

// How close a click was to the real place, and what that is worth. Distances are in the units of
// the map's own coordinate system (a 1000 x 700 viewBox, see WhereInMiddleEarthLocationBank), so
// the same click scores the same on every screen size.
//
// The bands are one rule for every difficulty. They are calibrated to the map: the bank refuses
// two places of one difficulty closer than twice the EXCELLENT radius, so no click can be
// "excellent" for two places at once, and the CLOSE radius (90) is under 10% of the map's width.
// Beyond it a click is FAR and earns nothing.
public enum WhereInMiddleEarthAccuracy {

    EXCELLENT(15.0, 100),
    VERY_GOOD(30.0, 80),
    GOOD(55.0, 60),
    CLOSE(90.0, 40),
    FAR(Double.POSITIVE_INFINITY, 0);

    public static final double EXCELLENT_RADIUS = EXCELLENT.maxDistance;

    public static final int MAX_POINTS = EXCELLENT.points;

    private final double maxDistance;
    private final int points;

    WhereInMiddleEarthAccuracy(double maxDistance, int points) {
        this.maxDistance = maxDistance;
        this.points = points;
    }

    // The largest distance, inclusive, that earns this band.
    public double maxDistance() {
        return maxDistance;
    }

    public int points() {
        return points;
    }

    public static WhereInMiddleEarthAccuracy forDistance(double distance) {
        for (WhereInMiddleEarthAccuracy accuracy : values()) {
            if (distance <= accuracy.maxDistance) {
                return accuracy;
            }
        }

        return FAR;
    }
}
