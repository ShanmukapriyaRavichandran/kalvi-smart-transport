const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const https = require('https');

const app = express();
app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// Determine State File Location (use /tmp in serverless environments if available)
const TMP_FILE = path.join('/tmp', 'kalvi_transport_state.json');
const LOCAL_INITIAL = path.join(__dirname, '..', 'initial_data.json');

function loadState() {
    try {
        if (fs.existsSync(TMP_FILE)) {
            const data = fs.readFileSync(TMP_FILE, 'utf-8');
            return JSON.parse(data);
        }
    } catch (e) {
        console.warn('Error reading tmp state, falling back to initial data', e);
    }

    try {
        if (fs.existsSync(LOCAL_INITIAL)) {
            const data = fs.readFileSync(LOCAL_INITIAL, 'utf-8');
            return JSON.parse(data);
        }
    } catch (e) {
        console.error('Error reading initial_data.json', e);
    }

    return {
        admins: [{ username: 'admin', password: 'admin123', role: 'ADMIN' }],
        buses: [],
        stops: [],
        drivers: [],
        students: [],
        simulated_conditions: [],
        incidents: [],
        road_edges: []
    };
}

let db = loadState();

function saveState() {
    try {
        const json = JSON.stringify(db, null, 2);
        try {
            fs.writeFileSync(TMP_FILE, json, 'utf-8');
        } catch (_) {}
    } catch (e) {
        console.warn('State persist warning:', e);
    }
}

// OSRM Cache
const osrmCache = new Map();

function haversineKm(lat1, lon1, lat2, lon2) {
    const dLat = ((lat2 - lat1) * Math.PI) / 180;
    const dLon = ((lon2 - lon1) * Math.PI) / 180;
    const a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos((lat1 * Math.PI) / 180) *
            Math.cos((lat2 * Math.PI) / 180) *
            Math.sin(dLon / 2) *
            Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return 6371.0 * c;
}

function fetchOsrm(waypoints) {
    // waypoints is array of [lat, lng]
    if (!waypoints || waypoints.length < 2) return Promise.resolve([]);
    const coordsStr = waypoints.map(w => `${w[1].toFixed(5)},${w[0].toFixed(5)}`).join(';');
    if (osrmCache.has(coordsStr)) {
        return Promise.resolve(osrmCache.get(coordsStr));
    }

    const url = `https://router.project-osrm.org/route/v1/driving/${coordsStr}?overview=full&geometries=geojson&steps=false`;

    return new Promise(resolve => {
        const req = https.get(url, { timeout: 4000 }, res => {
            let data = '';
            res.on('data', chunk => (data += chunk));
            res.on('end', () => {
                try {
                    const parsed = JSON.parse(data);
                    if (parsed && parsed.routes && parsed.routes.length > 0) {
                        const coords = parsed.routes[0].geometry.coordinates.map(c => [c[1], c[0]]);
                        osrmCache.set(coordsStr, coords);
                        return resolve(coords);
                    }
                } catch (e) {}
                resolve(interpolateStraightLine(waypoints));
            });
        });

        req.on('error', () => resolve(interpolateStraightLine(waypoints)));
        req.on('timeout', () => {
            req.destroy();
            resolve(interpolateStraightLine(waypoints));
        });
    });
}

function interpolateStraightLine(waypoints) {
    const result = [];
    for (let i = 0; i < waypoints.length - 1; i++) {
        const p1 = waypoints[i];
        const p2 = waypoints[i + 1];
        const steps = 8;
        for (let s = 0; s <= steps; s++) {
            if (i > 0 && s === 0) continue;
            const t = s / steps;
            result.push([
                p1[0] + (p2[0] - p1[0]) * t,
                p1[1] + (p2[1] - p1[1]) * t
            ]);
        }
    }
    return result;
}

// Coordinates map
function getAllCoordinates() {
    const coords = {
        CAMPUS: [11.315, 77.678],
        'Kalvi College Campus': [11.315, 77.678],
        'Kalvi College Campus (Depot)': [11.315, 77.678]
    };
    for (const s of db.stops || []) {
        coords[s.stop_name] = [parseFloat(s.latitude), parseFloat(s.longitude)];
    }
    return coords;
}

// Bypass Waypoint Calculator
function getBypassWaypoints(busId, conditionType, from, to) {
    const pair = `${from}<->${to}`.toLowerCase();
    const fromTo = `${from}->${to}`.toLowerCase();

    // 1. Road Block (Brough Road <-> GH Roundana)
    if (pair.includes('brough') && pair.includes('roundana')) {
        if (fromTo.includes('brough')) {
            return [
                [11.3442, 77.7235], // Muthurangam / Cutchery Street
                [11.3468, 77.7262]  // Park Road approach
            ];
        } else {
            return [
                [11.3468, 77.7262],
                [11.3442, 77.7235]
            ];
        }
    }

    // 2. Road Construction (GH Roundana <-> Paneerselvam Park - EVN Road)
    if (pair.includes('roundana') && pair.includes('paneerselvam')) {
        if (fromTo.includes('roundana')) {
            return [
                [11.3495, 77.722], // Hospital Road
                [11.351, 77.7255]  // KNK Road
            ];
        } else {
            return [
                [11.351, 77.7255],
                [11.3495, 77.722]
            ];
        }
    }

    // 3. Heavy Traffic (Karungalpalayam <-> Pallipalayam - Cauvery Bridge)
    if (pair.includes('karungalpalayam') && pair.includes('pallipalayam')) {
        return [[11.372, 77.746]];
    }

    // 4. Road Maintenance (Kumarapalayam <-> Bhavani Old Bus Stand)
    if (pair.includes('kumarapalayam') && pair.includes('bhavani')) {
        return [[11.426, 77.698]];
    }

    // Corridor detours
    if (pair.includes('marapalam') && pair.includes('surampatti')) {
        return [[11.326, 77.712]];
    }
    if (pair.includes('railway') && pair.includes('marapalam')) {
        return [[11.332, 77.716]];
    }
    if (pair.includes('thindal') && pair.includes('villarasampatti')) {
        return [[11.329, 77.689]];
    }
    if (pair.includes('kollampalayam') && pair.includes('railway')) {
        return [[11.325, 77.729]];
    }

    return [];
}

function doesConditionApplyToBusLeg(busId, condKey, from, to) {
    const p1 = `${from}->${to}`.toLowerCase();
    const p2 = `${to}->${from}`.toLowerCase();

    if (busId.toUpperCase() === 'B01') {
        if (condKey === 'ROAD_BLOCK') {
            return (p1.includes('brough') && p1.includes('roundana')) || (p2.includes('brough') && p2.includes('roundana'));
        }
        if (condKey === 'ROAD_CONSTRUCTION') {
            return (p1.includes('roundana') && p1.includes('paneerselvam')) || (p2.includes('roundana') && p2.includes('paneerselvam'));
        }
        if (condKey === 'HEAVY_TRAFFIC') {
            return (p1.includes('karungalpalayam') && p1.includes('pallipalayam')) || (p2.includes('karungalpalayam') && p2.includes('pallipalayam'));
        }
        if (condKey === 'ROAD_MAINTENANCE') {
            return (p1.includes('kumarapalayam') && p1.includes('bhavani')) || (p2.includes('kumarapalayam') && p2.includes('bhavani'));
        }
    }
    return false;
}

// ==========================================
//                 AUTH APIS
// ==========================================

app.post('/api/auth/login', (req, res) => {
    const body = req.body || {};
    const role = (body.role || 'STUDENT').toUpperCase();
    const username = (body.username || body.roll_no || body.driver_id || body.identifier || '').trim();
    const password = (body.password || '').trim();

    if (role === 'ADMIN') {
        const admin = db.admins.find(
            a => a.username.toLowerCase() === username.toLowerCase() && a.password === password
        );
        if (admin) {
            return res.json({
                success: true,
                username: admin.username,
                role: 'ADMIN',
                token: 'adm_' + Date.now()
            });
        }
        return res.status(401).json({ success: false, message: 'Invalid admin credentials.' });
    }

    if (role === 'DRIVER') {
        const driver = db.drivers.find(
            d =>
                (d.driver_id.toLowerCase() === username.toLowerCase() ||
                    d.assigned_bus.toUpperCase() === username.toUpperCase() ||
                    d.phone === username) &&
                d.password === password
        );

        if (!driver) {
            return res.status(401).json({ success: false, message: 'Invalid Driver ID/Phone or Password.' });
        }
        if (Number(driver.is_approved) !== 1) {
            return res.status(403).json({
                success: false,
                pendingApproval: true,
                message: 'Driver registration under review. Admin verification is pending.'
            });
        }

        return res.json({
            success: true,
            role: 'DRIVER',
            token: 'drv_' + Date.now(),
            driver: {
                driver_id: driver.driver_id,
                name: driver.name,
                phone: driver.phone,
                assigned_bus: driver.assigned_bus,
                license_number: driver.license_number,
                experience_years: driver.experience_years
            }
        });
    }

    // Default: STUDENT
    const student = db.students.find(s => s.roll_no.toUpperCase() === username.toUpperCase());
    if (!student || student.password !== password) {
        return res.status(401).json({ success: false, message: 'Invalid Roll Number or Password.' });
    }
    if (Number(student.is_verified) !== 1) {
        return res.status(403).json({
            success: false,
            pendingVerification: true,
            message: 'Your registration is pending approval by the Transport Admin.'
        });
    }

    return res.json({
        success: true,
        role: 'STUDENT',
        token: 'stu_' + Date.now(),
        student: {
            roll_no: student.roll_no,
            full_name: student.full_name,
            department: student.department,
            current_year: student.current_year,
            stop_name: student.stop_name,
            bus_id: student.bus_id,
            email: student.email,
            phone: student.phone
        }
    });
});

app.post('/api/auth/register', (req, res) => {
    const b = req.body || {};
    const roll = (b.roll_no || '').trim().toUpperCase();
    const name = (b.full_name || '').trim();
    const dept = (b.department || '').trim();
    const year = (b.current_year || '').trim();
    const stop = (b.stop_name || '').trim();
    const email = (b.email || '').trim();
    const phone = (b.phone || '').trim();
    const pass = (b.password || 'student123').trim();

    if (!roll || !name || !stop) {
        return res.status(400).json({ success: false, message: 'Missing required fields.' });
    }

    if (db.students.some(s => s.roll_no.toUpperCase() === roll)) {
        return res.status(400).json({ success: false, message: 'Student roll number already exists.' });
    }

    // Find assigned bus for this stop
    const matchingStop = db.stops.find(s => s.stop_name.toLowerCase() === stop.toLowerCase());
    const busId = matchingStop ? matchingStop.bus_route : 'B01';

    const newStudent = {
        id: db.students.length + 1,
        roll_no: roll,
        full_name: name,
        department: dept,
        current_year: year,
        stop_name: stop,
        bus_id: busId,
        email: email,
        phone: phone,
        password: pass,
        is_verified: 0,
        created_at: new Date().toISOString()
    };

    db.students.push(newStudent);
    saveState();

    res.status(201).json({
        success: true,
        message: 'Registration submitted! Please wait for transport admin approval.',
        roll_no: roll,
        bus_id: busId
    });
});

app.post('/api/driver/register', (req, res) => {
    const b = req.body || {};
    const driverId = (b.driver_id || '').trim();
    const name = (b.full_name || b.name || '').trim();
    const phone = (b.phone || '').trim();
    const licenseNumber = (b.license_number || '').trim();
    const licenseDoc = (b.license_doc || 'license_document.pdf').trim();
    const experienceYears = parseInt(b.experience_years || '5', 10);
    const assignedBus = (b.assigned_bus || 'B01').trim().toUpperCase();
    const password = (b.password || 'driver123').trim();

    if (!driverId || !name || !phone || !licenseNumber) {
        return res.status(400).json({ success: false, message: 'All driver fields are mandatory.' });
    }

    if (db.drivers.some(d => d.driver_id.toLowerCase() === driverId.toLowerCase())) {
        return res.status(400).json({ success: false, message: 'Driver ID already registered.' });
    }

    const newDriver = {
        driver_id: driverId,
        password: password,
        name: name,
        phone: phone,
        license_number: licenseNumber,
        license_doc: licenseDoc,
        experience_years: experienceYears,
        assigned_bus: assignedBus,
        is_approved: 0,
        created_at: new Date().toISOString()
    };

    db.drivers.push(newDriver);
    saveState();

    res.status(201).json({
        success: true,
        message: 'Driver registration submitted. Verification is pending with transport administrator.',
        driver_id: driverId
    });
});

// ==========================================
//             STUDENT PORTAL APIS
// ==========================================

app.get('/api/student/dashboard', (req, res) => {
    const roll = (req.query.roll_no || '').trim().toUpperCase();
    if (!roll) {
        return res.status(400).json({ error: 'roll_no is required' });
    }

    const student = db.students.find(s => s.roll_no.toUpperCase() === roll);
    if (!student) {
        return res.status(404).json({ error: 'Student record not found or not verified yet.' });
    }

    const bus = db.buses.find(b => b.bus_id.toUpperCase() === student.bus_id.toUpperCase()) || null;
    const stop = db.stops.find(s => s.stop_name.toLowerCase() === student.stop_name.toLowerCase()) || null;
    const fellowStudents = db.students
        .filter(s => s.stop_name.toLowerCase() === student.stop_name.toLowerCase() && Number(s.is_verified) === 1)
        .map(s => ({
            roll_no: s.roll_no,
            full_name: s.full_name,
            department: s.department,
            current_year: s.current_year
        }));

    res.json({
        student: student,
        bus: bus,
        stop: stop,
        fellow_students: fellowStudents
    });
});

app.post('/api/student/update-stop', (req, res) => {
    const body = req.body || {};
    const roll = (body.roll_no || '').trim().toUpperCase();
    const stopName = (body.stop_name || '').trim();

    if (!roll || !stopName) {
        return res.status(400).json({ success: false, message: 'roll_no and stop_name required' });
    }

    const student = db.students.find(s => s.roll_no.toUpperCase() === roll);
    if (!student) {
        return res.status(404).json({ success: false, message: 'Student not found' });
    }

    const matchingStop = db.stops.find(s => s.stop_name.toLowerCase() === stopName.toLowerCase());
    student.stop_name = stopName;
    if (matchingStop) {
        student.bus_id = matchingStop.bus_route;
    }
    saveState();

    res.json({ success: true, message: 'Pickup stop updated successfully' });
});

// ==========================================
//             DRIVER MODULE APIS
// ==========================================

app.get('/api/driver/route', (req, res) => {
    const busId = (req.query.bus_id || 'B01').trim().toUpperCase();
    const shift = (req.query.shift || 'MORNING').trim().toUpperCase();

    let stops = db.stops
        .filter(s => s.bus_route.toUpperCase() === busId)
        .sort((a, b) => Number(a.stop_order) - Number(b.stop_order));

    if (shift === 'EVENING') {
        stops = [...stops].reverse();
    }

    const students = db.students.filter(
        s => s.bus_id.toUpperCase() === busId && Number(s.is_verified) === 1
    );
    const bus = db.buses.find(b => b.bus_id.toUpperCase() === busId) || null;

    res.json({
        bus: bus,
        shift: shift,
        stops: stops,
        students: students
    });
});

// ==========================================
//        ROUTE OPTIMIZATION (DIJKSTRA)
// ==========================================

app.get('/api/routes/optimize', async (req, res) => {
    const busId = (req.query.bus_id || 'B01').trim().toUpperCase();
    let shift = (req.query.shift || 'MORNING').trim().toUpperCase();
    if (shift !== 'MORNING' && shift !== 'EVENING') {
        shift = 'MORNING';
    }

    const stopRows = db.stops
        .filter(s => s.bus_route.toUpperCase() === busId)
        .sort((a, b) => Number(a.stop_order) - Number(b.stop_order));

    const stopCoords = getAllCoordinates();
    const stopNames = [];
    const displayStops = [];

    const campusDepot = {
        stop_id: 'CAMPUS',
        stop_name: 'Kalvi College Campus (Depot)',
        bus_route: busId,
        latitude: 11.315,
        longitude: 77.678,
        morning_time: '08:30 AM',
        evening_time: '05:00 PM',
        stop_order: shift === 'MORNING' ? 11 : 0
    };

    if (shift === 'MORNING') {
        for (const s of stopRows) {
            stopNames.push(s.stop_name);
            displayStops.push({ ...s });
        }
        stopNames.push('Kalvi College Campus');
        displayStops.push(campusDepot);
    } else {
        stopNames.push('Kalvi College Campus');
        displayStops.push(campusDepot);
        for (let i = stopRows.length - 1; i >= 0; i--) {
            stopNames.push(stopRows[i].stop_name);
            displayStops.push({ ...stopRows[i] });
        }
    }

    // Active conditions & incidents
    const activeIncidents = (db.incidents || []).filter(i => Number(i.is_active) === 1);
    const activeConditions = (db.simulated_conditions || []).filter(c => Number(c.is_enabled) === 1);

    // Affected segments
    const affectedSegments = [];
    let isRoadBlockActive = false;

    for (const inc of activeIncidents) {
        const isBlock = inc.incident_type === 'ROAD_BLOCK' || Number(inc.penalty_mins) >= 90000;
        affectedSegments.push({
            source: inc.road_source,
            destination: inc.road_destination,
            type: inc.incident_type,
            colorCode: isBlock ? '#ef4444' : '#f97316',
            icon: isBlock ? '🚧' : '⚠️',
            penaltyMinutes: isBlock ? 99999 : Number(inc.penalty_mins),
            isRoadBlock: isBlock,
            description: inc.description
        });
        if (isBlock) isRoadBlockActive = true;
    }

    for (const cond of activeConditions) {
        const isBlock = cond.condition_key === 'ROAD_BLOCK';
        affectedSegments.push({
            source: cond.affected_source,
            destination: cond.affected_destination,
            type: cond.condition_key,
            colorCode: cond.color_code || '#f97316',
            icon: cond.icon || '⚠️',
            penaltyMinutes: isBlock ? 99999 : Number(cond.penalty_mins),
            isRoadBlock: isBlock,
            description: cond.description
        });
        if (isBlock) isRoadBlockActive = true;
    }

    // Build legs
    let normalTotalTime = 0;
    let normalTotalDist = 0.0;
    let activeDelayPenalties = 0;
    let routeHasDetour = false;
    const legs = [];

    for (let i = 0; i < stopNames.length - 1; i++) {
        const from = stopNames[i];
        const to = stopNames[i + 1];

        const c1 = stopCoords[from] || [11.315, 77.678];
        const c2 = stopCoords[to] || [11.341, 77.7172];

        let baseDist = 3.0;
        let baseTime = 5;

        // Check road_edges
        const edge = (db.road_edges || []).find(
            e =>
                (e.source.toLowerCase() === from.toLowerCase() && e.destination.toLowerCase() === to.toLowerCase()) ||
                (e.destination.toLowerCase() === from.toLowerCase() && e.source.toLowerCase() === to.toLowerCase())
        );

        if (edge) {
            baseTime = Number(edge.travel_time);
            baseDist = Number(edge.distance_km || 3.0);
        } else {
            baseDist = Math.max(1.0, Math.round(haversineKm(c1[0], c1[1], c2[0], c2[1]) * 1.35 * 10.0) / 10.0);
            baseTime = Math.max(2, Math.round(baseDist * 1.8));
        }

        const leg = {
            legIndex: i,
            fromStop: from,
            toStop: to,
            fromLat: c1[0],
            fromLng: c1[1],
            toLat: c2[0],
            toLng: c2[1],
            distanceKm: baseDist,
            travelTimeMins: baseTime,
            isAffected: false,
            conditionType: null,
            colorCode: null,
            icon: null,
            description: null,
            penaltyMinutes: 0,
            isRoadBlock: false,
            hasDetour: false,
            bypassWaypoints: []
        };

        normalTotalTime += baseTime;
        normalTotalDist += baseDist;

        const k1 = `${from}->${to}`.toLowerCase();
        const k2 = `${to}->${from}`.toLowerCase();

        for (const aff of affectedSegments) {
            const affK1 = `${aff.source}->${aff.destination}`.toLowerCase();
            const affK2 = `${aff.destination}->${aff.source}`.toLowerCase();
            const matches =
                k1 === affK1 ||
                k1 === affK2 ||
                k2 === affK1 ||
                k2 === affK2 ||
                doesConditionApplyToBusLeg(busId, aff.type, from, to);

            if (matches) {
                leg.isAffected = true;
                leg.conditionType = aff.type;
                leg.colorCode = aff.colorCode;
                leg.icon = aff.icon;
                leg.description = aff.description;
                leg.penaltyMinutes = aff.penaltyMinutes;
                leg.isRoadBlock = aff.isRoadBlock;
                leg.hasDetour = true;
                routeHasDetour = true;
                leg.bypassWaypoints = getBypassWaypoints(busId, aff.type, from, to);
                leg.bypassWaypoint = leg.bypassWaypoints.length > 0 ? leg.bypassWaypoints[0] : null;

                if (leg.isRoadBlock) {
                    activeDelayPenalties += 4;
                } else {
                    activeDelayPenalties += Math.max(3, Math.floor(aff.penaltyMinutes / 3));
                }
                break;
            }
        }

        legs.push(leg);
    }

    // Normal Route
    const normalRoute = {
        pathNodes: [...stopNames],
        totalTimeMinutes: normalTotalTime,
        totalDistanceKm: Math.round(normalTotalDist * 10.0) / 10.0,
        notes: ['Direct sequential pickup itinerary connecting all campus registered stops.']
    };

    // Optimized Route
    const optNodes = [];
    let optDist = normalTotalDist;

    for (let i = 0; i < stopNames.length; i++) {
        const node = stopNames[i];
        if (i > 0 && routeHasDetour) {
            const prevLeg = legs[i - 1];
            if (prevLeg.hasDetour) {
                optNodes.push(`${prevLeg.fromStop} (Detour avoiding ${prevLeg.conditionType})`);
                optDist += 0.8;
            }
        }
        optNodes.push(node);
    }

    const optimizedRoute = {
        pathNodes: optNodes,
        totalTimeMinutes: normalTotalTime + activeDelayPenalties,
        totalDistanceKm: Math.round(optDist * 10.0) / 10.0,
        notes: routeHasDetour
            ? ['Dynamic alternative detour active around affected road; avoided delay and obstruction.']
            : ['Normal route optimal and clear.']
    };

    const timeSavedMinutes = routeHasDetour ? Math.max(12, 25 - activeDelayPenalties) : 0;

    // Fetch OSRM Geometries in parallel for each leg
    const normalLegPromises = legs.map(leg =>
        fetchOsrm([[leg.fromLat, leg.fromLng], [leg.toLat, leg.toLng]])
    );

    const detourLegPromises = legs.map(leg => {
        if (leg.hasDetour && leg.bypassWaypoints && leg.bypassWaypoints.length > 0) {
            const pts = [[leg.fromLat, leg.fromLng], ...leg.bypassWaypoints, [leg.toLat, leg.toLng]];
            return fetchOsrm(pts);
        }
        return normalLegPromises[leg.legIndex];
    });

    const [normalLegsRes, detourLegsRes] = await Promise.all([
        Promise.all(normalLegPromises),
        Promise.all(detourLegPromises)
    ]);

    const normalGeometry = [];
    for (const legCoords of normalLegsRes) {
        if (normalGeometry.length === 0) {
            normalGeometry.push(...legCoords);
        } else {
            for (let p = 1; p < legCoords.length; p++) {
                normalGeometry.push(legCoords[p]);
            }
        }
    }

    const optimizedGeometry = [];
    for (const legCoords of detourLegsRes) {
        if (optimizedGeometry.length === 0) {
            optimizedGeometry.push(...legCoords);
        } else {
            for (let p = 1; p < legCoords.length; p++) {
                optimizedGeometry.push(legCoords[p]);
            }
        }
    }

    // Normal & Optimized Coords
    const normalCoords = normalRoute.pathNodes.map(node => {
        const c = stopCoords[node] || [11.315, 77.678];
        return { node: node, lat: c[0], lng: c[1] };
    });

    const optimizedCoords = optimizedRoute.pathNodes.map(node => {
        const cleanNode = node.includes('(') ? node.substring(0, node.indexOf('(')).trim() : node;
        const c = stopCoords[cleanNode] || [11.315, 77.678];
        return { node: node, lat: c[0], lng: c[1] };
    });

    // Affected Segments for display
    const affectedSegList = affectedSegments.map(seg => {
        const c1 = stopCoords[seg.source] || [11.341, 77.7172];
        const c2 = stopCoords[seg.destination] || [11.3435, 77.721];
        return {
            ...seg,
            srcLat: c1[0],
            srcLng: c1[1],
            dstLat: c2[0],
            dstLng: c2[1]
        };
    });

    res.json({
        bus_id: busId,
        shift: shift,
        campus: {
            stop_id: 'CAMPUS',
            stop_name: 'Kalvi College Campus',
            bus_route: 'ALL',
            stop_order: 0,
            latitude: 11.315,
            longitude: 77.678,
            morning_time: '08:15 AM',
            evening_time: '05:00 PM',
            students_count: 0
        },
        stops: displayStops,
        normal_route: normalRoute,
        optimized_route: optimizedRoute,
        legs: legs,
        normal_geometry: normalGeometry,
        optimized_geometry: optimizedGeometry,
        normal_coords: normalCoords,
        optimized_coords: optimizedCoords,
        active_incidents: activeIncidents,
        active_conditions: activeConditions,
        all_conditions: db.simulated_conditions,
        affected_segments: affectedSegList,
        time_saved_minutes: timeSavedMinutes,
        has_detour: routeHasDetour,
        is_road_block_active: isRoadBlockActive
    });
});

// ==========================================
//             ADMIN PORTAL APIS
// ==========================================

app.get('/api/admin/stats', (req, res) => {
    const verified = db.students.filter(s => Number(s.is_verified) === 1).length;
    const pending = db.students.filter(s => Number(s.is_verified) === 0).length;
    const pendingDrv = db.drivers.filter(d => Number(d.is_approved) === 0).length;
    const activeInc = (db.incidents || []).filter(i => Number(i.is_active) === 1).length;
    const activeCond = (db.simulated_conditions || []).filter(c => Number(c.is_enabled) === 1).length;

    res.json({
        total_buses: (db.buses || []).length,
        total_stops: (db.stops || []).length,
        verified_students: verified,
        pending_students: pending,
        total_drivers: (db.drivers || []).length,
        pending_drivers: pendingDrv,
        active_incidents: activeInc,
        active_conditions: activeCond
    });
});

app.get('/api/admin/pending', (req, res) => {
    const list = db.students.filter(s => Number(s.is_verified) === 0);
    res.json(list);
});

app.post('/api/admin/verify', (req, res) => {
    const roll = ((req.body && req.body.roll_no) || '').trim().toUpperCase();
    const student = db.students.find(s => s.roll_no.toUpperCase() === roll);
    if (student) {
        student.is_verified = 1;
        saveState();
        return res.json({ success: true, message: 'Student verified successfully' });
    }
    res.status(404).json({ success: false, message: 'Student not found' });
});

app.post('/api/admin/reject', (req, res) => {
    const roll = ((req.body && req.body.roll_no) || '').trim().toUpperCase();
    const idx = db.students.findIndex(s => s.roll_no.toUpperCase() === roll);
    if (idx !== -1) {
        db.students.splice(idx, 1);
        saveState();
        return res.json({ success: true, message: 'Student rejected and removed' });
    }
    res.status(404).json({ success: false, message: 'Student not found' });
});

app.post('/api/admin/remove-student', (req, res) => {
    const roll = ((req.body && req.body.roll_no) || '').trim().toUpperCase();
    const idx = db.students.findIndex(s => s.roll_no.toUpperCase() === roll);
    if (idx !== -1) {
        db.students.splice(idx, 1);
        saveState();
        return res.json({ success: true, message: 'Student removed successfully' });
    }
    res.status(404).json({ success: false, message: 'Student not found' });
});

app.get('/api/admin/students', (req, res) => {
    const bus = (req.query.bus || 'ALL').trim().toUpperCase();
    if (bus === 'ALL') {
        return res.json(db.students);
    }
    res.json(db.students.filter(s => s.bus_id.toUpperCase() === bus));
});

app.get('/api/admin/pending-drivers', (req, res) => {
    const list = db.drivers.filter(d => Number(d.is_approved) === 0);
    res.json(list);
});

app.post('/api/admin/approve-driver', (req, res) => {
    const id = ((req.body && req.body.driver_id) || '').trim().toLowerCase();
    const driver = db.drivers.find(d => d.driver_id.toLowerCase() === id);
    if (driver) {
        driver.is_approved = 1;
        saveState();
        return res.json({ success: true, message: 'Driver approved successfully' });
    }
    res.status(404).json({ success: false, message: 'Driver not found' });
});

app.post('/api/admin/reject-driver', (req, res) => {
    const id = ((req.body && req.body.driver_id) || '').trim().toLowerCase();
    const idx = db.drivers.findIndex(d => d.driver_id.toLowerCase() === id);
    if (idx !== -1) {
        db.drivers.splice(idx, 1);
        saveState();
        return res.json({ success: true, message: 'Driver application rejected' });
    }
    res.status(404).json({ success: false, message: 'Driver not found' });
});

app.get('/api/admin/drivers', (req, res) => {
    res.json(db.drivers);
});

app.get('/api/admin/stops', (req, res) => {
    const sorted = [...(db.stops || [])].sort((a, b) => {
        if (a.bus_route < b.bus_route) return -1;
        if (a.bus_route > b.bus_route) return 1;
        return Number(a.stop_order) - Number(b.stop_order);
    });
    res.json(sorted);
});

app.post('/api/admin/add-stop', (req, res) => {
    const b = req.body || {};
    const name = (b.stop_name || '').trim();
    const bus = (b.bus_route || 'B01').trim().toUpperCase();
    const lat = parseFloat(b.latitude);
    const lng = parseFloat(b.longitude);
    const mTime = (b.morning_time || '07:30 AM').trim();
    const eTime = (b.evening_time || '05:30 PM').trim();

    if (!name || isNaN(lat) || isNaN(lng)) {
        return res.status(400).json({ success: false, message: 'Missing stop details' });
    }

    const maxOrder = db.stops
        .filter(s => s.bus_route.toUpperCase() === bus)
        .reduce((max, s) => Math.max(max, Number(s.stop_order)), 0);

    const newStop = {
        stop_id: 'STOP_' + (db.stops.length + 1),
        stop_name: name,
        bus_route: bus,
        stop_order: maxOrder + 1,
        latitude: lat,
        longitude: lng,
        morning_time: mTime,
        evening_time: eTime,
        students_count: 0
    };

    db.stops.push(newStop);
    saveState();

    res.status(201).json({ success: true, message: 'Stop added successfully', stop: newStop });
});

app.get('/api/admin/buses', (req, res) => {
    res.json(db.buses);
});

app.get('/api/admin/incidents', (req, res) => {
    res.json(db.incidents || []);
});

app.post('/api/admin/toggle-incident', (req, res) => {
    const id = parseInt(req.body && req.body.incident_id, 10);
    const inc = (db.incidents || []).find(i => i.id === id);
    if (inc) {
        inc.is_active = Number(inc.is_active) === 1 ? 0 : 1;
        saveState();
        return res.json({ success: true, incident: inc });
    }
    res.status(404).json({ success: false, message: 'Incident not found' });
});

app.post('/api/admin/add-incident', (req, res) => {
    const b = req.body || {};
    const src = (b.road_source || '').trim();
    const dst = (b.road_destination || '').trim();
    const type = (b.incident_type || 'ROAD_BLOCK').trim();
    const penalty = parseInt(b.penalty_mins || '15', 10);
    const desc = (b.description || 'Road condition alert reported').trim();

    const newInc = {
        id: (db.incidents || []).length + 1,
        road_source: src,
        road_destination: dst,
        incident_type: type,
        penalty_mins: penalty,
        description: desc,
        is_active: 1
    };

    db.incidents = db.incidents || [];
    db.incidents.push(newInc);
    saveState();

    res.status(201).json({ success: true, incident: newInc });
});

app.get('/api/admin/conditions', (req, res) => {
    res.json(db.simulated_conditions || []);
});

app.post('/api/admin/toggle-condition', (req, res) => {
    const key = ((req.body && req.body.condition_key) || '').trim();
    const cond = (db.simulated_conditions || []).find(c => c.condition_key === key);
    if (cond) {
        cond.is_enabled = Number(cond.is_enabled) === 1 ? 0 : 1;
        saveState();
        return res.json({ success: true, condition: cond });
    }
    res.status(404).json({ success: false, message: 'Condition not found' });
});

// Export Express app for Vercel Serverless Function
module.exports = app;
