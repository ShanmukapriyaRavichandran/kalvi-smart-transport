public class StandardBus extends CollegeBus {

    public StandardBus(String busId, int capacity) {
        super(busId, capacity);
    }

    @Override
    public boolean canUseRoad(String roadType) {

        // Standard bus cannot use narrow roads
        if (roadType.equalsIgnoreCase("NARROW")) {
            return false;
        }

        return true;
    }
}