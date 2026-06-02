package at.pavlov.cannons.hooks.movecraft;

public record FirepowerReport(
    double cannonFirepower,
    double torpedoFirepower,
    double effectiveCannonFirepower,
    double torpedoOverflow,
    Integer maxCannonFirepower,
    Integer maxTorpedoFirepower
) {
    public boolean exceedsCannonLimit() {
        return maxCannonFirepower != null && effectiveCannonFirepower > maxCannonFirepower;
    }

    public boolean exceedsTorpedoLimitWithoutOverflow() {
        return maxTorpedoFirepower != null && torpedoFirepower > maxTorpedoFirepower && torpedoOverflow <= 0;
    }
}
