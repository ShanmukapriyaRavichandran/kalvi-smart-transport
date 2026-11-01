import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Attendance-aware college bus planner.
 * PRESENT + fee paid students are picked up on the normal route.
 * When a stop has fee-paid students but all are absent, that stop is skipped
 * and Dijkstra is used to find the shortest road path to the next eligible stop.
 * Only students and fee records loaded from the database are considered.
 */
public class TransportPlanner {

    private final TransportSystem system;
    private final PickupPlanningPolicy policy;
    private boolean morningMode = true;

    public void setMorningMode(boolean morningMode) { this.morningMode = morningMode; }

    public TransportPlanner(TransportSystem system, PickupPlanningPolicy policy) {
        this.system = system;
        this.policy = policy;
    }

    private boolean isFeePaid(Student student) {
        FeeRecord fee = system.getFeeRecords().get(student.getStudentId());
        return fee != null && fee.isPaid();
    }

    public void prepareWaitingStudents() {
        for (PickupStop stop : system.getStops().values()) {
            stop.getWaitingStudents().clear();
        }
        for (Student student : system.getStudents().values()) {
            student.setBoarded(false);
            if (student.isPresent() && isFeePaid(student)) {
                PickupStop stop = system.getStops().get(student.getStopId());
                if (stop != null) stop.addStudent(student);
            }
        }
    }

    private List<String> normalStopsForBus(String busId) {
        if ("B01".equalsIgnoreCase(busId)) return new ArrayList<>(Arrays.asList(DatabaseManager.B01_STOPS));
        if ("B02".equalsIgnoreCase(busId)) return new ArrayList<>(Arrays.asList(DatabaseManager.B02_STOPS));
        if ("B03".equalsIgnoreCase(busId)) return new ArrayList<>(Arrays.asList(DatabaseManager.B03_STOPS));
        return new ArrayList<>();
    }

    // Overflow passengers may be reassigned to B03 only after their originally
    // assigned bus is full. Their real registered stop is added to B03's route.
    private boolean eligibleForOverflowAllocation(Student student, CollegeBus receivingBus) {
        if (!"B03".equalsIgnoreCase(receivingBus.getBusId())) return false;
        if (student.isBoarded() || !student.isPresent() || !isFeePaid(student)) return false;
        if (receivingBus.getBusId().equalsIgnoreCase(student.getAssignedBusId())) return false;
        CollegeBus originalBus = system.getBuses().get(student.getAssignedBusId());
        return originalBus != null && originalBus.isFull();
    }

    private static class StopData {
        final String stopId;
        final List<Student> presentPaid = new ArrayList<>();
        int absentPaid;
        int registered;
        int unpaid;

        StopData(String stopId) { this.stopId = stopId; }
    }

    public String planTrip(CollegeBus bus) {
        DailyTrip trip = new DailyTrip(bus);
        List<String> normalStops = normalStopsForBus(bus.getBusId());
        // B03 can absorb eligible overflow passengers from full buses, using only
        // registered students and their existing database stops.
        if ("B03".equalsIgnoreCase(bus.getBusId())) {
            for (Student student : system.getStudents().values()) {
                if (eligibleForOverflowAllocation(student, bus) && !normalStops.contains(student.getStopId())) {
                    normalStops.add(student.getStopId());
                }
            }
        }
        Map<String, StopData> stopData = new java.util.LinkedHashMap<>();
        for (String stopId : normalStops) stopData.put(stopId, new StopData(stopId));

        // Read only registered students and their actual fee/attendance records.
        for (Student student : system.getStudents().values()) {
            boolean assignedToThisBus = bus.getBusId().equalsIgnoreCase(student.getAssignedBusId());
            boolean overflowToB03 = eligibleForOverflowAllocation(student, bus);
            if (!assignedToThisBus && !overflowToB03) continue;
            StopData data = stopData.get(student.getStopId());
            if (data == null) continue;
            data.registered++;
            if (!isFeePaid(student)) {
                data.unpaid++;
            } else if (student.isPresent()) {
                data.presentPaid.add(student);
            } else {
                data.absentPaid++;
            }
        }

        List<String> pendingStops = new ArrayList<>(normalStops);
        if (!morningMode) java.util.Collections.reverse(pendingStops);
        String current = "CAMPUS";
        int totalTime = 0;
        boolean dijkstraMode = false;
        boolean rerouteTriggered = false;
        StringBuilder details = new StringBuilder();
        details.append("\nTRIP DIRECTION: ").append(morningMode ? "MORNING (first eligible pickup stop -> CAMPUS)" : "EVENING (CAMPUS -> stops -> first eligible pickup stop)").append("\n");
        details.append("TRAVEL TIME: calculated by summing database road-edge weights for every driven leg.\n");
        details.append("\nATTENDANCE-BASED ROUTE DECISIONS (LIVE DATABASE RECORDS):\n");
        details.append("PRESENT + FEE PAID = pickup. ABSENT = skip pickup. After an absence, Dijkstra selects the nearest remaining eligible stop by road travel time.\n");
        if ("B03".equalsIgnoreCase(bus.getBusId())) details.append("CAPACITY REBALANCING: eligible, unboarded students from already-full buses may be picked up by B03 if seats remain.\n");
        details.append("Note: the shortest road path may pass through a skipped stop, but the bus does not stop there for pickup.\n");

        if (morningMode) {
            String firstEligible = null;
            for (String id : pendingStops) {
                StopData d = stopData.get(id);
                if (d != null && !d.presentPaid.isEmpty()) { firstEligible = id; break; }
            }
            if (firstEligible != null) {
                // Morning service begins at the first eligible pickup stop; no CAMPUS->stop leg is counted.
                for (int i = 0; i < pendingStops.size();) {
                    String id = pendingStops.get(i);
                    if (id.equals(firstEligible)) break;
                    StopData d = stopData.get(id);
                    if (d != null && d.presentPaid.isEmpty()) {
                        if (d.absentPaid > 0) details.append("[SKIP - ABSENT] ").append(id).append(": ").append(d.absentPaid).append(" fee-paid student(s) absent; stop excluded.\n");
                        else details.append("[SKIP - NO ELIGIBLE PICKUP] ").append(id).append(": no present student with paid fees; stop excluded.\n");
                    }
                    pendingStops.remove(i);
                }
                pendingStops.remove(firstEligible);
                trip.addStop(firstEligible);
                StopData firstData = stopData.get(firstEligible);
                details.append("[MORNING START] Bus starts at first eligible pickup stop: ").append(firstEligible).append(" (0 minutes before first pickup).\n");
                details.append("  Pickup: ").append(firstData.presentPaid.size()).append(" present, fee-paid student(s).\n");
                boardStudentsAtStop(bus, trip, firstData);
                current = firstEligible;
            } else {
                details.append("[NO PICKUPS] No present, fee-paid students are assigned to this bus today.\n");
            }
        }

        while (!pendingStops.isEmpty() && !bus.isFull()) {
            String nextStop;
            if (dijkstraMode) {
                // Remove stops that do not need a pickup before choosing the next destination.
                for (int i = 0; i < pendingStops.size();) {
                    String id = pendingStops.get(i);
                    StopData data = stopData.get(id);
                    if (data.presentPaid.isEmpty()) {
                        pendingStops.remove(i);
                        if (data.absentPaid > 0) {
                            details.append("[SKIP - ABSENT] ").append(id).append(": ")
                                   .append(data.absentPaid).append(" fee-paid student(s) absent; no pickup.\n");
                        } else if (data.registered > 0) {
                            details.append("[SKIP - NO ELIGIBLE PICKUP] ").append(id)
                                   .append(": no present student with paid fees (unpaid: ")
                                   .append(data.unpaid).append(").\n");
                        }
                    } else {
                        i++;
                    }
                }
                if (pendingStops.isEmpty()) break;

                nextStop = null;
                int bestTime = Integer.MAX_VALUE;
                List<String> bestPath = new ArrayList<>();
                for (String candidate : pendingStops) {
                    int candidateTime = shortestTravelTime(current, candidate);
                    if (candidateTime < bestTime) {
                        List<String> candidatePath = Dijkstra.shortestPathNodes(
                                system.getGraph(), current, candidate);
                        if (!candidatePath.isEmpty()) {
                            bestTime = candidateTime;
                            nextStop = candidate;
                            bestPath = candidatePath;
                        }
                    }
                }
                if (nextStop == null) {
                    details.append("[NO PATH] No connected road path from ").append(current)
                           .append(" to any remaining eligible pickup stop. Check road_edges.\n");
                    break;
                }
                pendingStops.remove(nextStop);
                details.append("\n[DIJKSTRA REROUTE EXECUTED] From ").append(current)
                       .append(", the shortest reachable eligible stop is ").append(nextStop)
                       .append(" ( ").append(bestTime).append(" minutes ).\n");
                details.append("  Shortest road path for driver: ")
                       .append(String.join(" -> ", bestPath)).append("\n");
                totalTime += bestTime;
                trip.addStop(nextStop);
                current = nextStop;
                StopData chosen = stopData.get(nextStop);
                details.append("  Pickup: ").append(chosen.presentPaid.size())
                       .append(" present, fee-paid student(s).\n");
                boardStudentsAtStop(bus, trip, chosen);
                continue;
            }

            // Before an absence is encountered, preserve the normal route order.
            nextStop = pendingStops.remove(0);
            StopData data = stopData.get(nextStop);
            if (data.presentPaid.isEmpty()) {
                if (data.absentPaid > 0) {
                    details.append("\n[SKIP - ABSENT] ").append(nextStop).append(": ")
                           .append(data.absentPaid).append(" fee-paid student(s) absent; pickup skipped.\n");
                    details.append("[NORMAL ROUTE INTERRUPTED] Dijkstra will now calculate the shortest path to the nearest remaining eligible stop.\n");
                    dijkstraMode = true;
                    rerouteTriggered = true;
                } else if (data.registered > 0) {
                    details.append("[SKIP - NO ELIGIBLE PICKUP] ").append(nextStop)
                           .append(": no present student with paid fees (unpaid: ")
                           .append(data.unpaid).append(").\n");
                }
                continue;
            }

            if (bus.isFull()) break;
            List<String> path = normalLegPath(current, nextStop);
            int legTime = directTravelTime(current, nextStop);
            boolean usedDijkstra = false;
            if (path.isEmpty() || legTime == Integer.MAX_VALUE) {
                path = Dijkstra.shortestPathNodes(system.getGraph(), current, nextStop);
                legTime = shortestTravelTime(current, nextStop);
                usedDijkstra = true;
            }
            if (path.isEmpty() || legTime == Integer.MAX_VALUE) {
                details.append("[NO PATH] Cannot find road path from ").append(current)
                       .append(" to ").append(nextStop).append(". Check road_edges.\n");
                continue;
            }
            details.append(usedDijkstra ? "[DIJKSTRA PATH] " : "[NORMAL ROUTE] ")
                   .append(current).append(" -> ").append(nextStop).append("\n")
                   .append("  Road path: ").append(String.join(" -> ", path)).append("\n")
                   .append("  Travel time: ").append(legTime).append(" minutes\n")
                   .append("  Pickup: ").append(data.presentPaid.size())
                   .append(" present, fee-paid student(s).\n");
            totalTime += legTime;
            trip.addStop(nextStop);
            current = nextStop;
            boardStudentsAtStop(bus, trip, data);
        }

        if (!trip.getRoute().isEmpty()) {
            String destination = morningMode ? "CAMPUS" : trip.getRoute().get(trip.getRoute().size() - 1);
            // Evening route ends at its first pickup stop; if already there, no extra leg is added.
            if (!current.equals(destination)) {
                List<String> returnPath = Dijkstra.shortestPathNodes(system.getGraph(), current, destination);
                int returnTime = shortestTravelTime(current, destination);
                if (!returnPath.isEmpty() && returnTime != Integer.MAX_VALUE) {
                    totalTime += returnTime;
                    details.append(morningMode ? "\n[END AT CAMPUS - DIJKSTRA] " : "\n[EVENING END AT FIRST PICKUP STOP - DIJKSTRA] ")
                           .append(String.join(" -> ", returnPath))
                           .append(" ( ").append(returnTime).append(" minutes )\n");
                } else {
                    details.append("\n[NO PATH] Cannot reach trip destination ").append(destination).append(" from ").append(current).append(".\n");
                }
            } else {
                details.append(morningMode ? "\n[END] Bus finishes at CAMPUS.\n" : "\n[END] Bus finishes at first pickup stop ").append(morningMode ? "" : destination + ".\n");
            }
        } else {
            details.append("\n[NO PICKUPS] No present, fee-paid students are assigned to this bus today.\n");
        }

        trip.completeTrip();
        return createTripReport(trip, details.toString(), totalTime, rerouteTriggered);
    }

    private void boardStudentsAtStop(CollegeBus bus, DailyTrip trip, StopData data) {
        for (Student student : data.presentPaid) {
            if (bus.isFull()) break;
            if (trip.assignStudent(student) && bus.boardStudent(student)) {
                PickupStop stop = system.getStops().get(data.stopId);
                if (stop != null) stop.getWaitingStudents().remove(student);
            }
        }
    }

    private List<String> normalLegPath(String source, String destination) {
        if (source.equals(destination)) return Arrays.asList(source);
        if (directTravelTime(source, destination) != Integer.MAX_VALUE) {
            return Arrays.asList(source, destination);
        }
        return new ArrayList<>();
    }

    private int directTravelTime(String source, String destination) {
        if (source.equals(destination)) return 0;
        for (Edge edge : system.getGraph().getNeighbors(source)) {
            if (destination.equals(edge.getDestination())) return edge.getTravelTime();
        }
        return Integer.MAX_VALUE;
    }

    private String createTripReport(DailyTrip trip, String details, int totalTravelTime, boolean rerouteTriggered) {
        StringBuilder report = new StringBuilder();
        List<String> route = trip.getRoute();
        report.append("\n========== DIJKSTRA ROUTE PLANNING ==========" ).append("\n");
        report.append("BUS: ").append(trip.getBus().getBusId()).append("\n");
        report.append("Algorithm: Normal route until an absent fee-paid student is found; then Dijkstra chooses the nearest remaining eligible stop.\n");
        if ("B03".equalsIgnoreCase(trip.getBus().getBusId())) report.append("Capacity allocation: eligible overflow students from full buses are included when seats and a road path are available.\n");
        report.append("Dijkstra rerouting triggered: ").append(rerouteTriggered ? "YES" : "NO").append("\n");
        report.append("Route stops: ");
        if (morningMode) {
            if (route.isEmpty()) report.append("No eligible pickup stops");
            else {
                report.append(String.join(" -> ", route));
                report.append(" -> CAMPUS");
            }
        } else {
            report.append("CAMPUS");
            for (String stop : route) report.append(" -> ").append(stop);
        }
        report.append("\nTotal calculated travel time: ").append(totalTravelTime).append(" minutes\n");
        report.append("Students boarded: ").append(trip.getAssignedStudents().size()).append(" / ")
                .append(trip.getBus().getCapacity()).append(" seats\n");
        report.append(details);
        report.append("=============================================\n");
        return report.toString();
    }

    private int shortestTravelTime(String source, String destination) {
        if (source.equals(destination)) return 0;
        Map<String, Integer> distances = Dijkstra.shortestPath(system.getGraph(), source);
        return distances.getOrDefault(destination, Integer.MAX_VALUE);
    }

    public void printUnallocatedStudents() {
        System.out.println("\n===== UNALLOCATED STUDENTS (PRESENT + FEE PAID) =====");
        boolean found = false;
        for (Student student : system.getStudents().values()) {
            if (student.isPresent() && isFeePaid(student) && !student.isBoarded()) {
                System.out.println(student.getStudentId() + " - " + student.getName());
                found = true;
            }
        }
        if (!found) System.out.println("No unallocated fee-paid, present students.");
    }
}
