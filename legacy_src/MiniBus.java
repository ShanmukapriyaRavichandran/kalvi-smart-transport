public class MiniBus extends CollegeBus {

    public MiniBus(String busId, int capacity) {
        super(busId, capacity);
    }

    @Override
    public boolean canUseRoad(String roadType) {

        // Mini bus can use narrow roads
        return true;
    }
}