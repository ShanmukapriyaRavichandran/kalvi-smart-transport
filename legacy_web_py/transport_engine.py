import heapq
from typing import Dict, List, Optional, Tuple


class WeightedGraph:
    """Weighted, undirected road network. Edge weights are travel minutes."""
    def __init__(self):
        self.adj: Dict[str, List[Tuple[str, int]]] = {}

    def add_vertex(self, vertex: str):
        self.adj.setdefault(vertex, [])

    def add_edge(self, source: str, destination: str, travel_time: int):
        self.add_vertex(source)
        self.add_vertex(destination)
        self.adj[source] = [(n, w) for n, w in self.adj[source] if n != destination]
        self.adj[destination] = [(n, w) for n, w in self.adj[destination] if n != source]
        self.adj[source].append((destination, int(travel_time)))
        self.adj[destination].append((source, int(travel_time)))

    def get_neighbors(self, vertex: str) -> List[Tuple[str, int]]:
        return self.adj.get(vertex, [])

    def get_vertices(self) -> List[str]:
        return list(self.adj.keys())

    def to_dict(self) -> Dict[str, List[Dict[str, object]]]:
        return {node: [{"destination": dst, "travel_time": wt} for dst, wt in edges]
                for node, edges in self.adj.items()}


class Dijkstra:
    """Dijkstra shortest paths with deterministic tie-breaking and path recovery."""
    @staticmethod
    def shortest_path(graph: WeightedGraph, source: str) -> Dict[str, int]:
        distances = {v: float('inf') for v in graph.get_vertices()}
        if source not in distances:
            distances[source] = 0
            return distances
        distances[source] = 0
        pq = [(0, source)]
        while pq:
            current_dist, current = heapq.heappop(pq)
            if current_dist > distances[current]:
                continue
            for neighbor, weight in graph.get_neighbors(current):
                candidate = current_dist + weight
                if candidate < distances.get(neighbor, float('inf')):
                    distances[neighbor] = candidate
                    heapq.heappush(pq, (candidate, neighbor))
        return distances

    @staticmethod
    def shortest_path_details(graph: WeightedGraph, source: str, destination: str) -> Tuple[List[str], int]:
        if source == destination:
            return [source], 0
        distances = {v: float('inf') for v in graph.get_vertices()}
        previous: Dict[str, str] = {}
        if source not in distances or destination not in distances:
            return [], float('inf')
        distances[source] = 0
        pq = [(0, source)]
        while pq:
            current_dist, current = heapq.heappop(pq)
            if current_dist > distances[current]:
                continue
            if current == destination:
                break
            for neighbor, weight in graph.get_neighbors(current):
                candidate = current_dist + weight
                if candidate < distances.get(neighbor, float('inf')):
                    distances[neighbor] = candidate
                    previous[neighbor] = current
                    heapq.heappush(pq, (candidate, neighbor))
        if distances.get(destination, float('inf')) == float('inf'):
            return [], float('inf')
        path = [destination]
        while path[-1] != source:
            parent = previous.get(path[-1])
            if parent is None:
                return [], float('inf')
            path.append(parent)
        path.reverse()
        return path, int(distances[destination])


class TransportEngine:
    """Plans each bus in its normal stop order, skipping empty pickup stops.

    When a stop has no eligible student (present + fee paid + authorized), the
    bus does not stop there. Dijkstra calculates the shortest road path to the
    next stop that actually needs a pickup. All students and fee states come
    from the database; this class never creates sample students.
    """
    NORMAL_ROUTES = {
        "B01": ["Erode Bus Stand", "Brough Road", "GH Roundana", "Paneerselvam Park",
                "Karungalpalayam", "Pallipalayam", "Kumarapalayam", "Bhavani Old Bus Stand",
                "Kalingarayanpalayam", "Chithode Junction"],
        "B02": ["Solar Pirivu", "Lakkapuram", "Kollampalayam Bypass", "Railway Station Gate",
                "Marapalam", "Surampatti Valasu", "Thindal Murugan Temple", "Villarasampatti",
                "Perundurai Old Bus Stand", "SIPCOT Perundurai Gate"],
        "B03": ["Modakkurichi Town", "Elumathur Junction", "Sivagiri Bus Stop", "Kodumudi Road Pirivu",
                "Muthur Corner", "Chennimalai Bus Stand", "Ingur Industrial Area", "Vijayamangalam Toll",
                "Uthukuli Road", "Nasiyanur Junction"]
    }

    def __init__(self, graph: WeightedGraph):
        self.graph = graph

    @staticmethod
    def _is_paid(student: dict) -> bool:
        return bool(student.get('fee_paid', student.get('paid', 0)))

    def plan_trips(self, buses: List[dict], students: List[dict], stops: List[dict]) -> dict:
        stop_by_id = {s['stop_id']: s for s in stops}
        students_by_bus_stop: Dict[Tuple[str, str], List[dict]] = {}
        all_registered_paid = 0
        present_eligible = []
        absent_paid_authorized = []

        for st in students:
            paid = self._is_paid(st)
            if paid:
                all_registered_paid += 1
            # Fee payment is the pickup eligibility gate for this module.
            if not paid:
                continue
            if st.get('present'):
                present_eligible.append(st)
                key = (st.get('assigned_bus_id') or '', st.get('stop_id') or '')
                students_by_bus_stop.setdefault(key, []).append(st)
            else:
                absent_paid_authorized.append(st)

        bus_plans = []
        boarded_ids = set()
        total_system_time = 0
        skipped_absent_total = 0

        for bus in buses:
            if bus.get('status', 'active') != 'active':
                continue
            bus_id = bus['bus_id']
            capacity = int(bus['capacity'])
            normal_route = self.NORMAL_ROUTES.get(bus_id, [])
            # Keep any DB-defined stops not in the known normal route at the end.
            db_bus_stops = [s['stop_id'] for s in stops if s.get('bus_route') == bus_id]
            ordered_stops = normal_route + [s for s in db_bus_stops if s not in normal_route]

            route = []
            route_legs = []
            decisions = []
            boarded_students = []
            current = 'CAMPUS'
            total_time = 0

            for stop_id in ordered_stops:
                assigned_paid = [st for st in students if
                                 (st.get('assigned_bus_id') == bus_id and st.get('stop_id') == stop_id
                                  and self._is_paid(st))]
                waiting = students_by_bus_stop.get((bus_id, stop_id), [])

                if not waiting:
                    absent_count = sum(1 for st in assigned_paid if not st.get('present'))
                    if absent_count:
                        skipped_absent_total += 1
                        decisions.append({
                            'stop_id': stop_id,
                            'status': 'SKIPPED_ABSENT',
                            'message': f'{stop_id}: {absent_count} registered fee-paid student(s) marked ABSENT; no pickup. Dijkstra will find the shortest path to the next required stop.'
                        })
                    continue

                if len(boarded_students) >= capacity:
                    decisions.append({'stop_id': stop_id, 'status': 'CAPACITY_FULL',
                                      'message': f'{stop_id}: bus capacity is full; pickup not possible.'})
                    continue

                path, leg_time = Dijkstra.shortest_path_details(self.graph, current, stop_id)
                if not path:
                    decisions.append({'stop_id': stop_id, 'status': 'UNREACHABLE',
                                      'message': f'{current} to {stop_id}: no connected road path found.'})
                    continue
                route_legs.append({'from': current, 'to': stop_id, 'path': path, 'time': leg_time,
                                   'reason': 'Dijkstra shortest path' if len(path) > 2 or current == 'CAMPUS' else 'Normal route segment'})
                total_time += leg_time
                current = stop_id
                route.append(stop_id)

                picked_here = 0
                for st in waiting:
                    if len(boarded_students) >= capacity:
                        break
                    if st['student_id'] in boarded_ids:
                        continue
                    boarded_students.append({'student_id': st['student_id'], 'name': st['name'], 'stop_id': st['stop_id']})
                    boarded_ids.add(st['student_id'])
                    picked_here += 1
                decisions.append({'stop_id': stop_id, 'status': 'PICKUP',
                                  'message': f'{stop_id}: {picked_here} present, fee-paid student(s) picked up.'})

            if route:
                path, return_time = Dijkstra.shortest_path_details(self.graph, current, 'CAMPUS')
                if path:
                    route_legs.append({'from': current, 'to': 'CAMPUS', 'path': path, 'time': return_time,
                                       'reason': 'Dijkstra shortest path back to campus'})
                    total_time += return_time
                else:
                    route_legs.append({'from': current, 'to': 'CAMPUS', 'path': [], 'time': None,
                                       'reason': 'No connected return path'})

            total_system_time += total_time
            bus_plans.append({
                'bus_id': bus_id,
                'bus_type': bus.get('bus_type', 'Standard Bus'),
                'capacity': capacity,
                'driver_name': bus.get('driver_name') or 'College Driver',
                'driver_phone': bus.get('driver_phone') or '',
                'status': bus.get('status', 'active'),
                'route': route,
                'full_path': ['CAMPUS'] + route + ['CAMPUS'] if route else ['CAMPUS'],
                'route_legs': route_legs,
                'route_decisions': decisions,
                'total_travel_time_mins': total_time,
                'trip_travel_time': total_time,
                'boarded_students': boarded_students,
                'boarded_count': len(boarded_students),
                'available_seats': max(0, capacity - len(boarded_students)),
                'is_full': len(boarded_students) >= capacity
            })

        unallocated_students = []
        for st in present_eligible:
            if st['student_id'] not in boarded_ids:
                unallocated_students.append({'student_id': st['student_id'], 'name': st['name'],
                                             'stop_id': st['stop_id'], 'reason': 'Bus capacity exhausted or no active assigned bus'})

        return {
            'bus_plans': bus_plans,
            'unallocated_students': unallocated_students,
            'unallocated_count': len(unallocated_students),
            'total_boarded_count': len(boarded_ids),
            'registered_student_count': len(students),
            'fee_paid_student_count': all_registered_paid,
            'eligible_present_count': len(present_eligible),
            'absent_fee_paid_authorized_count': len(absent_paid_authorized),
            'skipped_absent_stop_count': skipped_absent_total,
            'total_system_travel_time': total_system_time
        }
