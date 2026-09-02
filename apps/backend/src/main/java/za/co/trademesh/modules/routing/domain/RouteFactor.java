package za.co.trademesh.modules.routing.domain;

public enum RouteFactor {
    TIME("SECONDS", false),
    DISTANCE("METRES", false),
    FUEL("LITRES", false),
    TOLLS("ZAR", false),
    SAFETY_EXPOSURE("PERCENT", false),
    ROAD_QUALITY("PERCENT", true),
    CONNECTIVITY("PERCENT", true);

    private final String unit;
    private final boolean higherIsBetter;

    RouteFactor(String unit, boolean higherIsBetter) {
        this.unit = unit;
        this.higherIsBetter = higherIsBetter;
    }

    public String unit() {
        return unit;
    }

    public boolean higherIsBetter() {
        return higherIsBetter;
    }
}
