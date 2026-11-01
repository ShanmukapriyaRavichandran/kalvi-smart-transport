/**
 * Kalvi College Smart Transport System - Single-Page Frontend Logic
 * Connects directly to Java REST API on port 8080.
 * Interactive mapping with real OpenStreetMap tiles, OSRM real road-based routing,
 * driver verification workflow, secure role-based login, and dummy road condition simulation.
 */

// Application State
const state = {
    currentUser: JSON.parse(sessionStorage.getItem('kalvi_user')) || null,
    activeAuthRole: 'STUDENT',
    currentPortal: 'auth',
    selectedDriverBus: 'B01',
    selectedAdminBus: 'B01',
    attachedLicenseFile: null,
    maps: {
        student: null,
        driver: null,
        admin: null
    },
    markers: {
        student: [],
        driver: [],
        admin: []
    },
    polylines: {
        driverNormal: null,
        driverOpt: null,
        driverHazardSegments: [],
        adminNormal: null,
        adminOpt: null,
        adminHazardSegments: [],
        studentRoute: null
    },
    allStops: [],
    allStudents: [],
    allDrivers: [],
    simulatedConditions: [],
    pendingStudentsCount: 0,
    pendingDriversCount: 0
};

// Erode Campus Anchor Coordinates (Tamil Nadu)
const CAMPUS_COORDS = [11.3150, 77.6780];

// Initialize on DOM Ready
document.addEventListener('DOMContentLoaded', () => {
    initApp();
});

async function initApp() {
    await loadInitialData();
    setupStopDropdowns();
    updatePendingBadge();
    fetchSimulatedConditions();

    // Check if session already exists
    if (state.currentUser) {
        restoreUserSession();
    } else {
        showAuthView('STUDENT');
    }

    // Periodic background sync for pending approvals and condition changes (every 8s)
    setInterval(() => {
        updatePendingBadge();
        if (state.currentUser && state.currentUser.role === 'ADMIN') {
            fetchPendingStudents();
            fetchPendingDrivers();
            fetchAdminStats();
        }
    }, 8000);
}

// ==========================================
//     ROLE-BASED AUTH & VIEW NAVIGATION
// ==========================================

function showAuthView(role = 'STUDENT') {
    state.currentPortal = 'auth';
    hideAllPortals();
    document.getElementById('portal-auth').classList.add('active');
    const pa = document.getElementById('pill-auth');
    if (pa) pa.classList.add('active');
    switchAuthRole(role);
}

function showDriverSignupView() {
    state.currentPortal = 'driver-signup';
    hideAllPortals();
    document.getElementById('portal-driver-signup').classList.add('active');
    document.querySelectorAll('.nav-pill').forEach(p => p.classList.remove('active'));
    const pds = document.getElementById('pill-driver-signup');
    if (pds) pds.classList.add('active');
}

function switchAuthRole(role) {
    state.activeAuthRole = role;
    document.querySelectorAll('.role-switch-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.auth-role-form').forEach(f => f.style.display = 'none');

    const btn = document.getElementById(`btn-role-${role.toLowerCase()}`);
    const form = document.getElementById(`form-login-${role.toLowerCase()}`);
    if (btn) btn.classList.add('active');
    if (form) form.style.display = 'block';
}

function switchPortal(portal) {
    // Prevent unauthorized access across roles
    if (!state.currentUser) {
        showAuthView();
        showToast('Please sign in to access this portal.', 'error');
        return;
    }

    const role = state.currentUser.role;
    if (role === 'STUDENT' && portal !== 'student') {
        showToast('Access Denied: Student account cannot access other modules.', 'error');
        return;
    }
    if (role === 'DRIVER' && portal !== 'driver') {
        showToast('Access Denied: Driver account cannot access admin or student modules.', 'error');
        return;
    }
    if (role === 'ADMIN' && portal !== 'admin') {
        showToast('Use your administrator dashboard to manage modules.', 'info');
        return;
    }

    state.currentPortal = portal;
    hideAllPortals();
    document.querySelectorAll('.nav-pill').forEach(btn => btn.classList.remove('active'));

    const activeSec = document.getElementById(`portal-${portal}`);
    const activePill = document.getElementById(`pill-${portal}`);
    if (activeSec) activeSec.classList.add('active');
    if (activePill) activePill.classList.add('active');

    if (portal === 'student') {
        setTimeout(() => { if (state.maps.student) state.maps.student.invalidateSize(); }, 200);
    } else if (portal === 'driver') {
        setTimeout(() => {
            if (state.maps.driver) state.maps.driver.invalidateSize();
            else loadDriverBusRoute();
        }, 200);
    } else if (portal === 'admin') {
        setTimeout(() => {
            if (state.maps.admin) state.maps.admin.invalidateSize();
            else runAdminRouteOptimization();
        }, 200);
    }
}

function hideAllPortals() {
    document.querySelectorAll('.portal-section').forEach(sec => sec.classList.remove('active'));
    document.querySelectorAll('.nav-pill').forEach(pill => pill.classList.remove('active'));
}

function restoreUserSession() {
    if (!state.currentUser) return;
    const role = state.currentUser.role;

    // Configure role-aware navbar
    const pa = document.getElementById('pill-auth');
    if (pa) pa.style.display = 'none';
    const pds = document.getElementById('pill-driver-signup');
    if (pds) pds.style.display = 'none';
    document.getElementById('btn-logout').style.display = 'inline-block';

    const userBadge = document.getElementById('nav-user-badge');
    const userLabel = document.getElementById('nav-user-label');
    userBadge.style.display = 'flex';

    if (role === 'STUDENT') {
        userLabel.innerText = `🎓 ${state.currentUser.full_name} (${state.currentUser.roll_no})`;
        document.getElementById('pill-student').style.display = 'inline-flex';
        document.getElementById('pill-driver').style.display = 'none';
        document.getElementById('pill-admin').style.display = 'none';
        switchPortal('student');
        loadStudentDashboard(state.currentUser.roll_no);
    } else if (role === 'DRIVER') {
        userLabel.innerText = `🚌 ${state.currentUser.driver_name || state.currentUser.name} (Bus ${state.currentUser.assigned_bus})`;
        document.getElementById('pill-driver').style.display = 'inline-flex';
        document.getElementById('pill-student').style.display = 'none';
        document.getElementById('pill-admin').style.display = 'none';
        if (state.currentUser.assigned_bus) {
            document.getElementById('driver-bus-select').value = state.currentUser.assigned_bus;
        }
        switchPortal('driver');
        loadDriverBusRoute();
    } else if (role === 'ADMIN') {
        userLabel.innerText = `🛡️ Admin (${state.currentUser.username})`;
        document.getElementById('pill-admin').style.display = 'inline-flex';
        document.getElementById('pill-student').style.display = 'none';
        document.getElementById('pill-driver').style.display = 'none';
        switchPortal('admin');
        fetchAdminStats();
        fetchPendingStudents();
        fetchPendingDrivers();
        fetchVerifiedStudents();
        fetchStopsAndBuses();
        fetchIncidents();
        fetchSimulatedConditions();
        runAdminRouteOptimization();
    }
}

function logoutCurrentSession() {
    state.currentUser = null;
    sessionStorage.removeItem('kalvi_user');

    // Reset navbar visibility
    const pa = document.getElementById('pill-auth');
    if (pa) pa.style.display = 'inline-flex';
    const pds = document.getElementById('pill-driver-signup');
    if (pds) pds.style.display = 'inline-flex';
    document.getElementById('pill-student').style.display = 'none';
    document.getElementById('pill-driver').style.display = 'none';
    document.getElementById('pill-admin').style.display = 'none';
    document.getElementById('nav-user-badge').style.display = 'none';
    document.getElementById('btn-logout').style.display = 'none';

    // Reset views
    document.getElementById('student-dashboard-view').style.display = 'none';
    document.getElementById('student-pending-view').style.display = 'none';

    showAuthView();
    showToast('Signed out successfully.', 'info');
}

// ==========================================
//           STUDENT PORTAL
// ==========================================

async function handleStudentLogin(e) {
    e.preventDefault();
    const rollNo = document.getElementById('student-roll').value.trim();
    const password = document.getElementById('student-pass').value;

    try {
        const res = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ role: 'STUDENT', username: rollNo, password: password })
        });
        const data = await res.json();

        if (res.status === 200 && data.success) {
            state.currentUser = data;
            sessionStorage.setItem('kalvi_user', JSON.stringify(data));
            restoreUserSession();
            showToast(`Welcome back, ${data.full_name}!`, 'success');
        } else if (res.status === 403 || data.pendingVerification) {
            // Pending Verification View
            hideAllPortals();
            document.getElementById('portal-student').classList.add('active');
            document.getElementById('student-pending-view').style.display = 'flex';
            document.getElementById('student-dashboard-view').style.display = 'none';
            document.getElementById('pending-roll-display').innerText = rollNo.toUpperCase();
        } else {
            showToast(data.message || 'Login failed. Check your roll number and password.', 'error');
        }
    } catch (err) {
        showToast('Error communicating with server.', 'error');
    }
}

async function loadStudentDashboard(rollNo) {
    try {
        const res = await fetch(`/api/student/dashboard?roll_no=${encodeURIComponent(rollNo)}`);
        const data = await res.json();

        if (!res.ok) {
            showToast(data.error || 'Failed loading dashboard', 'error');
            return;
        }

        const student = data.student;
        const bus = data.bus || {};
        const fellow = data.fellow_students || [];

        document.getElementById('student-pending-view').style.display = 'none';
        document.getElementById('student-dashboard-view').style.display = 'block';

        document.getElementById('student-profile-name').innerText = student.full_name;
        document.getElementById('student-profile-dept').innerText = `Roll No: ${student.roll_no} | ${student.department} (${student.current_year})`;
        document.getElementById('student-bus-badge').innerText = `Bus ${student.bus_id}`;

        // Pass details
        document.getElementById('pass-name').innerText = student.full_name;
        document.getElementById('pass-roll').innerText = student.roll_no;
        document.getElementById('pass-bus').innerText = `${student.bus_id} (${bus.bus_type || 'College Bus'})`;
        document.getElementById('pass-stop').innerText = student.stop_name;

        // Current Stop Badge & Dropdown in Student Module
        await fetchAllStopsForDropdowns();
        const curBadge = document.getElementById('student-current-stop-badge');
        if (curBadge) curBadge.innerText = `Current: ${student.stop_name}`;
        const stopSelect = document.getElementById('student-stop-select');
        if (stopSelect) stopSelect.value = student.stop_name;

        // Driver details
        document.getElementById('student-driver-name').innerText = bus.driver_name || 'Assigned Driver';
        document.getElementById('student-driver-phone').innerText = bus.driver_phone || '+91 98421 11001';

        // Fellow students list
        const fellowList = document.getElementById('fellow-students-list');
        if (fellow.length === 0) {
            fellowList.innerHTML = '<p class="text-muted">You are currently the first verified passenger at this stop.</p>';
        } else {
            fellowList.innerHTML = fellow.map(f => `
                <div class="mini-list-item">
                    <span><strong>${escapeHtml(f.full_name)}</strong> (${f.department})</span>
                    <span class="badge badge-primary">${f.roll_no}</span>
                </div>
            `).join('');
        }

        // Render Student Map with real road-based routing
        renderStudentRoadMap(student.bus_id, student.stop_name);

    } catch (e) {
        showToast('Error loading student dashboard.', 'error');
    }
}

async function handleStudentChangeStop() {
    const select = document.getElementById('student-stop-select');
    const newStop = select ? select.value : '';
    if (!newStop) {
        showToast('Please select a valid stop.', 'warning');
        return;
    }
    if (!state.currentUser || !state.currentUser.roll_no) {
        showToast('Student session not found. Please log in.', 'error');
        return;
    }
    const rollNo = state.currentUser.roll_no;
    try {
        const res = await fetch('/api/student/update-stop', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ roll_no: rollNo, stop_name: newStop })
        });
        const data = await res.json();
        if (res.ok && data.success) {
            showToast(`Boarding stop updated to "${newStop}" (Bus ${data.bus_id})!`, 'success');
            state.currentUser.stop_name = newStop;
            state.currentUser.bus_id = data.bus_id;
            sessionStorage.setItem('kalvi_user', JSON.stringify(state.currentUser));
            await loadStudentDashboard(rollNo);
        } else {
            showToast(data.message || 'Failed to update stop.', 'error');
        }
    } catch (e) {
        showToast('Error communicating with server.', 'error');
    }
}

function renderStudentShiftMap() {
    if (state.currentUser && state.currentUser.bus_id) {
        renderStudentRoadMap(state.currentUser.bus_id, state.currentUser.stop_name);
    }
}

async function renderStudentRoadMap(busId, boardingStop) {
    try {
        const shift = document.getElementById('student-shift-select') ? document.getElementById('student-shift-select').value : 'MORNING';
        const res = await fetch(`/api/routes/optimize?bus_id=${busId}&shift=${shift}`);
        const data = await res.json();

        if (!state.maps.student) {
            state.maps.student = L.map('student-map-container').setView(CAMPUS_COORDS, 12);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '© OpenStreetMap contributors | OSRM Driving Engine'
            }).addTo(state.maps.student);
        }

        const map = state.maps.student;
        state.markers.student.forEach(m => map.removeLayer(m));
        state.markers.student = [];
        if (state.polylines.studentNormal) map.removeLayer(state.polylines.studentNormal);
        state.polylines.studentNormal = null;
        if (state.polylines.studentOpt) map.removeLayer(state.polylines.studentOpt);
        state.polylines.studentOpt = null;
        if (state.polylines.studentHazardSegments) {
            state.polylines.studentHazardSegments.forEach(seg => map.removeLayer(seg));
        }
        state.polylines.studentHazardSegments = [];

        const stops = data.stops || [];
        const isEvening = shift === 'EVENING';

        // 1. Render Numbered Stop Markers & Campus Depot based on Shift
        stops.forEach((st, idx) => {
            const isCampus = st.stop_id === 'CAMPUS' || (st.stop_name || '').toLowerCase().includes('campus');
            if (isCampus) {
                const campusIcon = L.divIcon({
                    className: 'custom-stop-div',
                    html: `<div class="stop-marker-pin campus-pin">🏫</div>`,
                    iconSize: [34, 34],
                    iconAnchor: [17, 17]
                });
                const cm = L.marker([st.latitude, st.longitude], { icon: campusIcon }).addTo(map);
                cm.bindPopup(`<b>🏫 Kalvi College Campus</b><br>${isEvening ? 'Shift Origin (Departs 05:00 PM)' : 'Final Campus Destination (Arrives 08:30 AM)'}`);
                state.markers.student.push(cm);
            } else {
                const isBoarding = (st.stop_name || '').toLowerCase() === (boardingStop || '').toLowerCase();
                let displayNum = st.stop_order;
                if (!displayNum || displayNum > 10 || displayNum < 1) {
                    displayNum = isEvening ? (stops.length - idx) : (idx + 1);
                }

                const iconHtml = `<div class="stop-marker-pin ${isBoarding ? 'boarding-pin' : 'normal-pin'}">${displayNum}</div>`;
                const icon = L.divIcon({
                    className: 'custom-stop-div',
                    html: iconHtml,
                    iconSize: [28, 28],
                    iconAnchor: [14, 14]
                });

                const m = L.marker([st.latitude, st.longitude], { icon: icon }).addTo(map);
                m.bindPopup(`
                    <div style="font-size:13px;">
                        <strong>Stop #${displayNum}: ${escapeHtml(st.stop_name)}</strong><br>
                        <span>${isEvening ? 'Evening Drop: <strong>' + st.evening_time + '</strong>' : 'Morning Pickup: <strong>' + st.morning_time + '</strong>'}</span><br>
                        ${isBoarding ? '<div class="badge badge-warning mt-1">⭐ Your Allocated Boarding Stop</div>' : ''}
                    </div>
                `);
                if (isBoarding) m.openPopup();
                state.markers.student.push(m);
            }
        });

        // 2. Normal Route in BLUE
        const normalGeo = data.normal_geometry || [];
        if (normalGeo.length > 1) {
            state.polylines.studentNormal = L.polyline(normalGeo, {
                color: '#2563eb', // Solid vibrant BLUE
                weight: 5,
                opacity: 0.8
            }).addTo(map);
            state.polylines.studentNormal.bindPopup(`<b>🟦 Normal Route (${shift})</b><br>Follows standard assigned road corridor.`);
        }

        // 3. Dynamic Detour Route in RED when any issue is active
        // BOTH ROUTES REMAIN VISIBLE AT THE SAME TIME!
        if (data.has_detour && data.optimized_geometry && data.optimized_geometry.length > 1) {
            state.polylines.studentOpt = L.polyline(data.optimized_geometry, {
                color: '#ef4444', // Highlighted RED
                weight: 6,
                opacity: 0.95,
                dashArray: '10, 6'
            }).addTo(map);
            state.polylines.studentOpt.bindPopup(`<b>🟥 Dynamically Optimized Detour Route</b><br>Active detour avoiding road hazard.<br>Time saved: <b>${data.time_saved_minutes} mins</b>`);
        }

        // 4. Affected Segment Hazard Markers (Positioned directly on the blocked road)
        const legs = data.legs || [];
        for (const leg of legs) {
            if (leg.isAffected) {
                // Hazard is on the direct road between consecutive stops
                const hazardLat = (leg.fromLat + leg.toLat) / 2;
                const hazardLng = (leg.fromLng + leg.toLng) / 2;

                const alertIcon = L.divIcon({
                    className: 'custom-stop-div',
                    html: `<div style="font-size:24px; filter:drop-shadow(0 0 6px ${leg.colorCode || '#ef4444'}); cursor:pointer;" title="${escapeHtml(leg.conditionType)}">${leg.icon || '⚠️'}</div>`,
                    iconSize: [30, 30],
                    iconAnchor: [15, 15]
                });
                const am = L.marker([hazardLat, hazardLng], { icon: alertIcon }).addTo(map);
                am.bindPopup(`
                    <div style="font-size:13px;">
                        <strong>${leg.icon || '⚠️'} ${escapeHtml(leg.conditionType || 'Active Hazard')}</strong><br>
                        <span>Direct road: <b>${escapeHtml(leg.fromStop)}</b> ➔ <b>${escapeHtml(leg.toStop)}</b></span><br>
                        <span style="color:#ef4444; font-weight:600;">🚫 Blocked road barred from traversal.</span><br>
                        <span style="color:#10b981; font-weight:600;">✅ Bus reaches <b>${escapeHtml(leg.toStop)}</b> via alternative smallest route.</span>
                    </div>
                `);
                state.markers.student.push(am);
            }
        }

        if (state.polylines.studentNormal) {
            if (state.polylines.studentOpt) {
                const group = new L.featureGroup([state.polylines.studentNormal, state.polylines.studentOpt]);
                map.fitBounds(group.getBounds(), { padding: [35, 35] });
            } else {
                map.fitBounds(state.polylines.studentNormal.getBounds(), { padding: [35, 35] });
            }
        }

    } catch (e) {
        console.error('Student road map error:', e);
    }
}

// ==========================================
//       STUDENT REGISTRATION MODAL
// ==========================================

async function openRegisterModal() {
    await fetchAllStopsForDropdowns();
    document.getElementById('modal-register').classList.add('active');
}

function closeRegisterModal() {
    document.getElementById('modal-register').classList.remove('active');
}

async function handleStudentRegister(e) {
    e.preventDefault();
    const name = document.getElementById('reg-name').value.trim();
    const roll = document.getElementById('reg-roll').value.trim().toUpperCase();
    const dept = document.getElementById('reg-dept').value;
    const year = document.getElementById('reg-year').value;
    const stop = document.getElementById('reg-stop').value;
    const email = document.getElementById('reg-email').value.trim();
    const phone = document.getElementById('reg-phone').value.trim();
    const pass = document.getElementById('reg-pass').value;
    const pass2 = document.getElementById('reg-pass2').value;

    if (pass !== pass2) {
        showToast('Passwords do not match.', 'error');
        return;
    }

    try {
        const res = await fetch('/api/auth/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                roll_no: roll,
                full_name: name,
                department: dept,
                current_year: year,
                stop_name: stop,
                email: email,
                phone: phone,
                password: pass
            })
        });
        const data = await res.json();

        if (res.status === 201 && data.success) {
            closeRegisterModal();
            document.getElementById('student-reg-form').reset();
            showToast('Registration submitted! Queued for Admin Verification.', 'success');

            // Show pending screen
            hideAllPortals();
            document.getElementById('portal-student').classList.add('active');
            document.getElementById('pending-roll-display').innerText = roll;
            document.getElementById('student-pending-view').style.display = 'flex';

            updatePendingBadge();
        } else {
            showToast(data.message || 'Registration failed.', 'error');
        }
    } catch (err) {
        showToast('Error connecting to backend.', 'error');
    }
}

// ==========================================
//       DRIVER SIGNUP & VERIFICATION
// ==========================================

function handleLicenseFileSelected(e) {
    const file = e.target.files[0];
    if (!file) return;

    if (file.size > 5 * 1024 * 1024) {
        showToast('File size exceeds 5MB limit. Please choose a smaller file.', 'error');
        e.target.value = '';
        return;
    }

    state.attachedLicenseFile = {
        name: file.name,
        size: (file.size / 1024).toFixed(1) + ' KB',
        type: file.type
    };

    document.getElementById('license-file-prompt').innerText = `Attached: ${file.name}`;
    document.getElementById('license-file-name').innerText = `${file.name} (${state.attachedLicenseFile.size})`;
    document.getElementById('license-preview-container').style.display = 'flex';
}

function clearLicenseFile() {
    state.attachedLicenseFile = null;
    document.getElementById('drv-signup-doc').value = '';
    document.getElementById('license-file-prompt').innerText = 'Click to select Driving License image or PDF';
    document.getElementById('license-preview-container').style.display = 'none';
}

async function handleDriverSignupSubmit(e) {
    e.preventDefault();
    const name = document.getElementById('drv-signup-name').value.trim();
    const phone = document.getElementById('drv-signup-phone').value.trim();
    const licenseNo = document.getElementById('drv-signup-license').value.trim();
    const exp = parseInt(document.getElementById('drv-signup-exp').value) || 0;
    const bus = document.getElementById('drv-signup-bus').value;
    const driverId = document.getElementById('drv-signup-id').value.trim();
    const pass = document.getElementById('drv-signup-pass').value;
    const pass2 = document.getElementById('drv-signup-pass2').value;

    if (pass !== pass2) {
        showToast('Passwords do not match.', 'error');
        return;
    }

    const docName = state.attachedLicenseFile ? state.attachedLicenseFile.name : `license_${driverId}.pdf`;

    try {
        const res = await fetch('/api/driver/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                driver_id: driverId,
                full_name: name,
                phone: phone,
                license_number: licenseNo,
                license_doc: docName,
                experience_years: exp,
                assigned_bus: bus,
                password: pass
            })
        });
        const data = await res.json();

        if (res.status === 201 && data.success) {
            document.getElementById('driver-signup-form').reset();
            clearLicenseFile();
            document.getElementById('driver-signup-form').style.display = 'none';
            document.getElementById('pending-driver-id-display').innerText = data.driver_id || driverId;
            document.getElementById('driver-pending-screen').style.display = 'block';
            showToast('Driver application registered! Awaiting admin license approval.', 'success');
            updatePendingBadge();
        } else {
            showToast(data.message || 'Driver registration failed.', 'error');
        }
    } catch (err) {
        showToast('Error submitting driver application.', 'error');
    }
}

async function handleDriverLogin(e) {
    e.preventDefault();
    const identifier = document.getElementById('driver-identifier').value.trim();
    const password = document.getElementById('driver-pass').value;

    try {
        const res = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ role: 'DRIVER', username: identifier, password: password })
        });
        const data = await res.json();

        if (res.status === 200 && data.success) {
            state.currentUser = data;
            sessionStorage.setItem('kalvi_user', JSON.stringify(data));
            restoreUserSession();
            showToast(`Welcome, Driver ${data.driver_name}!`, 'success');
        } else if (res.status === 403 || data.pendingApproval) {
            showToast(data.message || 'Your driver registration is pending admin approval.', 'warning');
        } else {
            showToast(data.message || 'Invalid driver credentials.', 'error');
        }
    } catch (err) {
        showToast('Error communicating with server.', 'error');
    }
}

// ==========================================
//             DRIVER MODULE
// ==========================================

async function loadDriverBusRoute() {
    const busId = document.getElementById('driver-bus-select').value;
    const shift = document.getElementById('driver-shift-select') ? document.getElementById('driver-shift-select').value : 'MORNING';
    state.selectedDriverBus = busId;

    try {
        const [drvRes, optRes] = await Promise.all([
            fetch(`/api/driver/route?bus_id=${busId}&shift=${shift}`).then(r => r.json()),
            fetch(`/api/routes/optimize?bus_id=${busId}&shift=${shift}`).then(r => r.json())
        ]);

        const stops = optRes.stops || drvRes.stops || [];
        const students = drvRes.students || [];

        document.getElementById('drv-stat-stops').innerText = stops.length;
        document.getElementById('drv-stat-students').innerText = students.length;
        if (document.getElementById('drv-stat-departure')) {
            document.getElementById('drv-stat-departure').innerText = shift === 'EVENING' ? '05:00 PM' : '06:45 AM';
        }

        // Render Scheduled Stops
        const stopsTbody = document.getElementById('driver-stops-tbody');
        const isEvening = shift === 'EVENING';
        stopsTbody.innerHTML = stops.map((s, idx) => {
            const isCampus = s.stop_id === 'CAMPUS' || (s.stop_name || '').toLowerCase().includes('campus');
            let displayNum = s.stop_order;
            if (isCampus) displayNum = isEvening ? 'DEPOT' : 'FINAL';
            else if (!displayNum || displayNum > 10 || displayNum < 1) {
                displayNum = isEvening ? (stops.length - idx) : (idx + 1);
            }
            return `
                <tr>
                    <td><span class="badge ${isCampus ? 'badge-success' : 'badge-primary'}">${displayNum}</span></td>
                    <td><strong>${escapeHtml(s.stop_name)}</strong></td>
                    <td><span class="badge badge-info">${isEvening ? (s.evening_time || '05:00 PM') : (s.morning_time || '07:30 AM')}</span></td>
                    <td><span class="badge badge-success">${s.student_count || (isCampus ? '-' : 3)} Students</span></td>
                </tr>
            `;
        }).join('');

        // Render Verified Passenger List
        const passTbody = document.getElementById('driver-passengers-tbody');
        if (students.length === 0) {
            passTbody.innerHTML = '<tr><td colspan="4" class="text-center">No passengers registered on this bus yet.</td></tr>';
        } else {
            passTbody.innerHTML = students.map(st => `
                <tr>
                    <td><code>${st.roll_no}</code></td>
                    <td><strong>${escapeHtml(st.full_name)}</strong> (${st.department})</td>
                    <td>${escapeHtml(st.stop_name)}</td>
                    <td><a href="tel:${st.phone}" class="badge badge-success">📞 ${st.phone}</a></td>
                </tr>
            `).join('');
        }

        // Live Detour / Road Condition Banner
        const hazardBanner = document.getElementById('driver-hazard-banner');
        if (optRes.has_detour || optRes.is_road_block_active) {
            hazardBanner.style.display = 'flex';
            document.getElementById('driver-hazard-text').innerHTML =
                `<strong>Dynamic Alternative Detour Active!</strong> Route optimizer avoided road hazards/delays. Delay saved: <strong>${optRes.time_saved_minutes} mins</strong>. Detour highlighted in <strong>RED</strong> on map.`;
        } else {
            hazardBanner.style.display = 'none';
        }

        // Metrics strip
        const norm = optRes.normal_route || {};
        const opt = optRes.optimized_route || {};
        document.getElementById('drv-norm-time').innerText = `${norm.totalTimeMinutes || 0} mins`;
        document.getElementById('drv-norm-dist').innerText = `${norm.totalDistanceKm || 0} km`;
        document.getElementById('drv-opt-time').innerText = `${opt.totalTimeMinutes || 0} mins`;
        document.getElementById('drv-opt-dist').innerText = `${opt.totalDistanceKm || 0} km`;
        document.getElementById('drv-time-saved').innerText = optRes.time_saved_minutes > 0 ?
            `${optRes.time_saved_minutes} mins saved!` : 'Route Optimal';

        // Render Map with real road geometry
        await renderDriverRoadMap(optRes);

        // Keep Driver Quick Condition chips in sync with active states
        fetchSimulatedConditions();

    } catch (e) {
        console.error('Error loading driver route:', e);
    }
}

async function renderDriverRoadMap(optData) {
    if (!state.maps.driver) {
        state.maps.driver = L.map('driver-map-container').setView(CAMPUS_COORDS, 12);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '© OpenStreetMap | OSRM Driving Engine'
        }).addTo(state.maps.driver);
    }

    const map = state.maps.driver;

    // Clear previous
    state.markers.driver.forEach(m => map.removeLayer(m));
    state.markers.driver = [];
    if (state.polylines.driverNormal) map.removeLayer(state.polylines.driverNormal);
    state.polylines.driverNormal = null;
    if (state.polylines.driverOpt) map.removeLayer(state.polylines.driverOpt);
    state.polylines.driverOpt = null;
    state.polylines.driverHazardSegments.forEach(seg => map.removeLayer(seg));
    state.polylines.driverHazardSegments = [];

    const stops = optData.stops || [];
    const shift = optData.shift || 'MORNING';
    const isEvening = shift === 'EVENING';

    // 1. Render Stop Markers & Campus Marker based on shift sequence
    stops.forEach((st, idx) => {
        const isCampus = st.stop_id === 'CAMPUS' || (st.stop_name || '').toLowerCase().includes('campus');
        if (isCampus) {
            const campusIcon = L.divIcon({
                className: 'custom-stop-div',
                html: `<div class="stop-marker-pin campus-pin">🏫</div>`,
                iconSize: [34, 34],
                iconAnchor: [17, 17]
            });
            const cm = L.marker([st.latitude, st.longitude], { icon: campusIcon }).addTo(map);
            cm.bindPopup(`<b>🏫 Kalvi College Campus (Depot)</b><br>${isEvening ? 'Shift Origin (Departs 05:00 PM)' : 'Final Campus Destination (Arrives 08:30 AM)'}`);
            state.markers.driver.push(cm);
        } else {
            let displayNum = st.stop_order;
            if (!displayNum || displayNum > 10 || displayNum < 1) {
                displayNum = isEvening ? (stops.length - idx) : (idx + 1);
            }
            const icon = L.divIcon({
                className: 'custom-stop-div',
                html: `<div class="stop-marker-pin normal-pin">${displayNum}</div>`,
                iconSize: [28, 28],
                iconAnchor: [14, 14]
            });
            const m = L.marker([st.latitude, st.longitude], { icon: icon }).addTo(map);
            m.bindPopup(`
                <div style="font-size:13px;">
                    <strong>Stop #${displayNum}: ${escapeHtml(st.stop_name)}</strong><br>
                    <span>${isEvening ? 'Evening Drop: <strong>' + st.evening_time + '</strong>' : 'Morning Pickup: <strong>' + st.morning_time + '</strong>'}</span><br>
                    <span class="badge badge-info mt-1">${st.student_count || 0} Registered Students</span>
                </div>
            `);
            state.markers.driver.push(m);
        }
    });

    // 2. Render Normal Route in BLUE
    // Normal route is ALWAYS visible
    const normalGeo = optData.normal_geometry || [];
    if (normalGeo.length > 1) {
        state.polylines.driverNormal = L.polyline(normalGeo, {
            color: '#2563eb', // Solid vibrant BLUE
            weight: 5,
            opacity: 0.8
        }).addTo(map);
        state.polylines.driverNormal.bindPopup(`<b>🟦 Normal Driver Route (${shift})</b><br>Baseline scheduled corridor.`);
    }

    // 3. Render Optimized Detour Route in RED when an issue is active
    // BOTH ROUTES REMAIN VISIBLE AT THE SAME TIME!
    if (optData.has_detour && optData.optimized_geometry && optData.optimized_geometry.length > 1) {
        state.polylines.driverOpt = L.polyline(optData.optimized_geometry, {
            color: '#ef4444', // Highlighted RED
            weight: 6,
            opacity: 0.95,
            dashArray: '10, 6'
        }).addTo(map);
        state.polylines.driverOpt.bindPopup(`<b>🟥 Dynamically Optimized Detour Route</b><br>Active detour avoiding road hazard.<br>Time saved: <b>${optData.time_saved_minutes} mins</b>`);
    }

    // 4. Affected Segment Hazard Markers (Positioned directly on the blocked road)
    const legs = optData.legs || [];
    for (const leg of legs) {
        if (leg.isAffected) {
            // Hazard is on the direct road between consecutive stops
            const hazardLat = (leg.fromLat + leg.toLat) / 2;
            const hazardLng = (leg.fromLng + leg.toLng) / 2;

            const alertIcon = L.divIcon({
                className: 'custom-stop-div',
                html: `<div style="font-size:24px; filter:drop-shadow(0 0 6px ${leg.colorCode || '#ef4444'}); cursor:pointer;" title="${escapeHtml(leg.conditionType)}">${leg.icon || '⚠️'}</div>`,
                iconSize: [30, 30],
                iconAnchor: [15, 15]
            });
            const am = L.marker([hazardLat, hazardLng], { icon: alertIcon }).addTo(map);
            am.bindPopup(`
                <div style="font-size:13px;">
                    <strong>${leg.icon || '⚠️'} ${escapeHtml(leg.conditionType || 'Hazard')}</strong><br>
                    <span>Direct road: <b>${escapeHtml(leg.fromStop)}</b> ➔ <b>${escapeHtml(leg.toStop)}</b></span><br>
                    <span style="color:#ef4444; font-weight:600;">🚫 Blocked road barred from traversal.</span><br>
                    <span style="color:#10b981; font-weight:600;">✅ Bus reaches <b>${escapeHtml(leg.toStop)}</b> via alternative smallest route.</span>
                </div>
            `);
            state.markers.driver.push(am);
        }
    }

    if (state.polylines.driverNormal) {
        if (state.polylines.driverOpt) {
            const group = new L.featureGroup([state.polylines.driverNormal, state.polylines.driverOpt]);
            map.fitBounds(group.getBounds(), { padding: [35, 35] });
        } else {
            map.fitBounds(state.polylines.driverNormal.getBounds(), { padding: [35, 35] });
        }
    }
}

function switchDriverTab(tab) {
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.driver-tab-content').forEach(c => c.classList.remove('active'));

    document.getElementById(`driver-tab-${tab}`).classList.add('active');
    event.target.classList.add('active');
}

function openDriverReportModal() {
    document.getElementById('modal-driver-report').classList.add('active');
}

function closeDriverReportModal() {
    document.getElementById('modal-driver-report').classList.remove('active');
}

async function handleDriverReport(e) {
    e.preventDefault();
    const type = document.getElementById('drv-rep-type').value;
    const desc = document.getElementById('drv-rep-desc').value.trim();

    const busId = state.selectedDriverBus;
    const src = busId === 'B01' ? 'Brough Road' : (busId === 'B02' ? 'Marapalam' : 'Muthur Corner');
    const dst = busId === 'B01' ? 'GH Roundana' : (busId === 'B02' ? 'Surampatti Valasu' : 'Chennimalai Bus Stand');

    try {
        const res = await fetch('/api/admin/add-incident', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                road_source: src,
                road_destination: dst,
                incident_type: type,
                penalty_mins: 18,
                description: desc
            })
        });

        if (res.ok) {
            closeDriverReportModal();
            showToast('Hazard reported! Re-routing in real-time...', 'warning');
            loadDriverBusRoute();
        }
    } catch (e) {
        showToast('Error broadcasting incident.', 'error');
    }
}

// ==========================================
//             ADMIN PORTAL
// ==========================================

async function handleAdminLogin(e) {
    e.preventDefault();
    const u = document.getElementById('admin-user').value.trim();
    const p = document.getElementById('admin-pass').value;

    try {
        const res = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ role: 'ADMIN', username: u, password: p })
        });
        const data = await res.json();

        if (res.ok && data.success) {
            state.currentUser = data;
            sessionStorage.setItem('kalvi_user', JSON.stringify(data));
            restoreUserSession();
            showToast('Admin authenticated successfully.', 'success');
        } else {
            showToast(data.message || 'Invalid admin credentials.', 'error');
        }
    } catch (e) {
        showToast('Server communication error.', 'error');
    }
}

function switchAdminTab(tab) {
    document.querySelectorAll('.adm-tab').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.admin-tab-panel').forEach(p => p.classList.remove('active'));

    const tabBtn = document.getElementById(`adm-tab-${tab}`);
    const tabView = document.getElementById(`admin-view-${tab}`);
    if (tabBtn) tabBtn.classList.add('active');
    if (tabView) tabView.classList.add('active');

    if (tab === 'optimizer') {
        setTimeout(() => {
            if (state.maps.admin) state.maps.admin.invalidateSize();
            else runAdminRouteOptimization();
        }, 200);
    } else if (tab === 'conditions') {
        fetchSimulatedConditions();
    } else if (tab === 'pending-drivers') {
        fetchPendingDrivers();
    } else if (tab === 'pending-students') {
        fetchPendingStudents();
    }
}

async function fetchAdminStats() {
    try {
        const res = await fetch('/api/admin/stats');
        const s = await res.json();

        document.getElementById('metric-buses').innerText = s.total_buses || 3;
        document.getElementById('metric-stops').innerText = s.total_stops || 30;
        document.getElementById('metric-students').innerText = s.verified_students || 0;
        document.getElementById('metric-pending').innerText = s.pending_students || 0;
        document.getElementById('metric-pending-drivers').innerText = s.pending_drivers || 0;
        document.getElementById('metric-conditions').innerText = s.active_conditions || 0;

        state.pendingStudentsCount = s.pending_students || 0;
        state.pendingDriversCount = s.pending_drivers || 0;
        updatePendingBadge();
    } catch (e) {
        console.error('Error fetching stats:', e);
    }
}

function updatePendingBadge() {
    Promise.all([
        fetch('/api/admin/pending').then(r => r.json()).catch(() => []),
        fetch('/api/admin/pending-drivers').then(r => r.json()).catch(() => [])
    ]).then(([students, drivers]) => {
        state.pendingStudentsCount = students.length;
        state.pendingDriversCount = drivers.length;
        const total = students.length + drivers.length;

        const navBadge = document.getElementById('nav-pending-badge');
        const studentTabBadge = document.getElementById('tab-pending-count');
        const driverTabBadge = document.getElementById('tab-pending-drivers-count');

        if (total > 0) {
            navBadge.innerText = total;
            navBadge.style.display = 'inline-block';
        } else {
            navBadge.style.display = 'none';
        }

        if (studentTabBadge) studentTabBadge.innerText = students.length;
        if (driverTabBadge) driverTabBadge.innerText = drivers.length;
    }).catch(() => {});
}

// ==========================================
//    SIMULATED ROAD CONDITION TOGGLES API
// ==========================================

async function fetchSimulatedConditions() {
    try {
        const res = await fetch('/api/admin/conditions');
        const conditions = await res.json();
        state.simulatedConditions = conditions;

        renderSimulatedConditionsCards(conditions);
        renderQuickSimulationChips(conditions);
    } catch (e) {
        console.error('Error fetching simulated conditions:', e);
    }
}

function renderSimulatedConditionsCards(conditions) {
    const container = document.getElementById('simulated-conditions-grid');
    if (!container) return;

    container.innerHTML = conditions.map(c => {
        const isEnabled = c.is_enabled === 1;
        return `
            <div class="condition-toggle-card glass-panel ${isEnabled ? 'card-condition-active' : ''}">
                <div class="condition-card-top">
                    <div class="condition-icon-badge" style="background: ${c.color_code}22; border-color: ${c.color_code};">
                        <span>${c.icon}</span>
                    </div>
                    <label class="switch">
                        <input type="checkbox" ${isEnabled ? 'checked' : ''} onchange="toggleSimulatedCondition('${c.condition_key}', this.checked)">
                        <span class="slider round" style="${isEnabled ? `background-color: ${c.color_code};` : ''}"></span>
                    </label>
                </div>

                <div class="condition-card-info">
                    <div class="condition-title-row">
                        <h4>${escapeHtml(c.condition_name)}</h4>
                        <span class="badge ${isEnabled ? 'badge-danger' : 'badge-outline'}">
                            ${isEnabled ? 'SIMULATED HAZARD ON' : 'NORMAL'}
                        </span>
                    </div>
                    <p class="condition-desc">${escapeHtml(c.description)}</p>

                    <div class="condition-meta-strip">
                        <span>📍 Affected: <strong>${escapeHtml(c.affected_source)} ➔ ${escapeHtml(c.affected_destination)}</strong></span>
                        <span>⏱️ Delay Effect: <strong>+${c.penalty_mins} mins</strong></span>
                    </div>
                </div>
            </div>
        `;
    }).join('');
}

function renderQuickSimulationChips(conditions) {
    const adminContainer = document.getElementById('quick-toggle-chips-container');
    const driverContainer = document.getElementById('driver-quick-toggle-chips-container');
    if (!adminContainer && !driverContainer) return;

    const html = conditions.map(c => {
        const isEnabled = c.is_enabled === 1;
        return `
            <button class="quick-chip-btn ${isEnabled ? 'chip-active' : ''}" 
                    style="${isEnabled ? `border-color: ${c.color_code}; color: ${c.color_code}; box-shadow: 0 0 10px ${c.color_code}44;` : ''}"
                    onclick="toggleSimulatedCondition('${c.condition_key}', ${!isEnabled})"
                    title="${escapeHtml(c.condition_name)}">
                <span>${c.icon}</span>
                <span>${escapeHtml(c.condition_name.split(' ')[0])}</span>
                <span class="chip-status-dot ${isEnabled ? 'on' : 'off'}"></span>
            </button>
        `;
    }).join('');

    if (adminContainer) adminContainer.innerHTML = html;
    if (driverContainer) driverContainer.innerHTML = html;
}

async function toggleSimulatedCondition(key, enable) {
    try {
        const isEnabledInt = enable ? 1 : 0;
        const res = await fetch('/api/admin/toggle-condition', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ condition_key: key, is_enabled: isEnabledInt })
        });

        if (res.ok) {
            const statusMsg = enable ? `activated (+penalty applied, detours recalculated)` : `resolved (normal routing restored)`;
            showToast(`Simulated condition '${key}' ${statusMsg}.`, enable ? 'warning' : 'success');

            // Refresh toggles & re-optimize map dynamically WITHOUT page reload!
            await fetchSimulatedConditions();
            if (state.maps.admin || state.currentPortal === 'admin') {
                await runAdminRouteOptimization();
            }
            if (state.maps.driver || state.currentPortal === 'driver') {
                await loadDriverBusRoute();
            }
            if (state.maps.student || state.currentPortal === 'student') {
                renderStudentShiftMap();
            }
            fetchAdminStats();
        } else {
            showToast('Failed to toggle condition.', 'error');
        }
    } catch (e) {
        showToast('Error communicating with backend.', 'error');
    }
}

// ==========================================
//      ADMIN DRIVER APPROVALS QUEUE
// ==========================================

async function fetchPendingDrivers() {
    try {
        const res = await fetch('/api/admin/pending-drivers');
        const list = await res.json();

        const tbody = document.getElementById('pending-drivers-tbody');
        if (list.length === 0) {
            tbody.innerHTML = '<tr><td colspan="9" class="text-center text-muted">All driver applications verified! Queue is clean.</td></tr>';
            return;
        }

        tbody.innerHTML = list.map(d => `
            <tr>
                <td><code>${d.driver_id}</code></td>
                <td><strong>${escapeHtml(d.name)}</strong></td>
                <td><a href="tel:${d.phone}" class="badge badge-info">${d.phone}</a></td>
                <td><strong>${escapeHtml(d.license_number)}</strong></td>
                <td>
                    <button class="btn btn-outline btn-sm" onclick='openLicenseViewerModal(${JSON.stringify(d)})'>
                        👁️ Inspect License (${escapeHtml(d.license_doc || 'doc.pdf')})
                    </button>
                </td>
                <td>${d.experience_years} Years</td>
                <td><span class="badge badge-primary">${d.assigned_bus}</span></td>
                <td>${d.created_at || 'Just now'}</td>
                <td>
                    <button class="btn btn-primary btn-sm" onclick="approveDriver('${d.driver_id}', '${d.assigned_bus}')">✅ Approve</button>
                    <button class="btn btn-outline btn-sm" onclick="rejectDriver('${d.driver_id}')">❌ Reject</button>
                </td>
            </tr>
        `).join('');

        fetchAllDrivers();
    } catch (e) {
        console.error('Pending drivers fetch error:', e);
    }
}

async function fetchAllDrivers() {
    try {
        const res = await fetch('/api/admin/drivers');
        const drivers = await res.json();
        const tbody = document.getElementById('all-drivers-tbody');
        if (!tbody) return;

        tbody.innerHTML = drivers.map(d => {
            const isApproved = d.is_approved === 1;
            return `
                <tr>
                    <td><code>${d.driver_id}</code></td>
                    <td><strong>${escapeHtml(d.name)}</strong></td>
                    <td>${d.phone}</td>
                    <td>${escapeHtml(d.license_number)}</td>
                    <td><span class="badge badge-primary">${d.assigned_bus}</span></td>
                    <td>${d.experience_years} Years</td>
                    <td><span class="badge ${isApproved ? 'badge-success' : 'badge-warning'}">${isApproved ? 'Verified & Active' : 'Pending Verification'}</span></td>
                </tr>
            `;
        }).join('');
    } catch (e) {}
}

function openLicenseViewerModal(driver) {
    document.getElementById('view-doc-driver-name').innerText = driver.name;
    document.getElementById('view-doc-license-num').innerText = driver.license_number;
    document.getElementById('view-doc-phone').innerText = driver.phone;
    document.getElementById('view-doc-exp').innerText = `${driver.experience_years} Years of Commercial Driving`;
    document.getElementById('view-doc-file-info').innerText = `Document File: ${driver.license_doc || 'commercial_dl.pdf'}`;

    const approveBtn = document.getElementById('btn-modal-approve-driver');
    approveBtn.onclick = () => {
        closeLicenseViewerModal();
        approveDriver(driver.driver_id, driver.assigned_bus);
    };

    document.getElementById('modal-license-viewer').classList.add('active');
}

function closeLicenseViewerModal() {
    document.getElementById('modal-license-viewer').classList.remove('active');
}

async function approveDriver(driverId, assignedBus) {
    try {
        const res = await fetch('/api/admin/approve-driver', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ driver_id: driverId, assigned_bus: assignedBus })
        });
        const data = await res.json();

        if (res.ok && data.success) {
            showToast(`Driver ${driverId} APPROVED! Driving license verified and login enabled.`, 'success');
            fetchPendingDrivers();
            fetchAdminStats();
            updatePendingBadge();
        } else {
            showToast('Approval failed.', 'error');
        }
    } catch (e) {
        showToast('Error communicating with server.', 'error');
    }
}

async function rejectDriver(driverId) {
    if (!confirm(`Reject driver application for ${driverId}?`)) return;

    try {
        const res = await fetch('/api/admin/reject-driver', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ driver_id: driverId })
        });
        const data = await res.json();

        if (res.ok && data.success) {
            showToast(`Driver application for ${driverId} was rejected.`, 'info');
            fetchPendingDrivers();
            fetchAdminStats();
            updatePendingBadge();
        }
    } catch (e) {
        showToast('Error communicating with server.', 'error');
    }
}

// ==========================================
//      ADMIN STUDENT VERIFICATION
// ==========================================

async function fetchPendingStudents() {
    try {
        const res = await fetch('/api/admin/pending');
        const list = await res.json();

        const tbody = document.getElementById('pending-students-tbody');
        if (list.length === 0) {
            tbody.innerHTML = '<tr><td colspan="9" class="text-center text-muted">All registrations verified! Queue is clean.</td></tr>';
            return;
        }

        tbody.innerHTML = list.map(st => `
            <tr>
                <td><code>${st.roll_no}</code></td>
                <td><strong>${escapeHtml(st.full_name)}</strong></td>
                <td><span class="badge badge-info">${st.department}</span></td>
                <td>${st.current_year}</td>
                <td><strong>${escapeHtml(st.stop_name)}</strong></td>
                <td><span class="badge badge-primary">${st.bus_id}</span></td>
                <td>${escapeHtml(st.email)}</td>
                <td>${escapeHtml(st.phone)}</td>
                <td>
                    <button class="btn btn-primary btn-sm" onclick="verifyStudent('${st.roll_no}')">✅ Verify</button>
                    <button class="btn btn-outline btn-sm" onclick="rejectStudent('${st.roll_no}')">❌ Reject</button>
                </td>
            </tr>
        `).join('');
    } catch (e) {
        console.error('Pending fetch error:', e);
    }
}

async function verifyStudent(rollNo) {
    try {
        const res = await fetch('/api/admin/verify', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ roll_no: rollNo })
        });
        const data = await res.json();

        if (res.ok && data.success) {
            showToast(`Student ${rollNo} verified! Login enabled.`, 'success');
            fetchPendingStudents();
            fetchAdminStats();
            fetchVerifiedStudents();
            updatePendingBadge();
        } else {
            showToast('Verification failed.', 'error');
        }
    } catch (e) {
        showToast('Error communicating with server.', 'error');
    }
}

async function rejectStudent(rollNo) {
    if (!confirm(`Reject and delete registration for ${rollNo}?`)) return;

    try {
        const res = await fetch('/api/admin/reject', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ roll_no: rollNo })
        });
        const data = await res.json();

        if (res.ok && data.success) {
            showToast(`Registration for ${rollNo} removed.`, 'info');
            fetchPendingStudents();
            fetchAdminStats();
            updatePendingBadge();
        }
    } catch (e) {
        showToast('Error removing registration.', 'error');
    }
}

async function removeStudent(rollNo, fullName) {
    const displayName = fullName ? `${fullName} (${rollNo})` : rollNo;
    if (!confirm(`Are you sure you want to permanently remove student ${displayName}? This action cannot be undone.`)) {
        return;
    }

    try {
        const res = await fetch('/api/admin/remove-student', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ roll_no: rollNo })
        });
        const data = await res.json();

        if (res.ok && data.success) {
            showToast(`Student ${displayName} has been removed.`, 'info');
            fetchVerifiedStudents();
            fetchAdminStats();
            fetchStopsAndBuses();
        } else {
            showToast(data.message || 'Failed to remove student.', 'error');
        }
    } catch (e) {
        showToast('Error communicating with server.', 'error');
    }
}

async function fetchVerifiedStudents() {
    try {
        const res = await fetch('/api/admin/students?bus=ALL');
        const list = await res.json();
        state.allStudents = list;
        renderVerifiedStudentsTable(list);
    } catch (e) {
        console.error('Verified students fetch error:', e);
    }
}

function renderVerifiedStudentsTable(list) {
    const tbody = document.getElementById('verified-students-tbody');
    if (list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" class="text-center">No verified students match criteria.</td></tr>';
        return;
    }

    tbody.innerHTML = list.map(st => `
        <tr>
            <td><code>${st.roll_no}</code></td>
            <td><strong>${escapeHtml(st.full_name)}</strong></td>
            <td>${st.department}</td>
            <td>${st.current_year}</td>
            <td><span class="badge badge-primary">${st.bus_id}</span></td>
            <td>${escapeHtml(st.stop_name)}</td>
            <td>${escapeHtml(st.email)}</td>
            <td>${escapeHtml(st.phone)}</td>
            <td>
                <button class="btn btn-outline btn-sm btn-danger-soft" onclick="removeStudent('${st.roll_no}', '${escapeHtml(st.full_name)}')" title="Remove Student">
                    🗑️ Remove
                </button>
            </td>
        </tr>
    `).join('');
}

function filterStudentsTable() {
    const q = document.getElementById('student-search-input').value.toLowerCase();
    const bus = document.getElementById('student-bus-filter').value;

    const filtered = state.allStudents.filter(st => {
        const matchesQuery = st.full_name.toLowerCase().includes(q) || st.roll_no.toLowerCase().includes(q) || st.department.toLowerCase().includes(q);
        const matchesBus = bus === 'ALL' || st.bus_id === bus;
        return matchesQuery && matchesBus;
    });

    renderVerifiedStudentsTable(filtered);
}

// ==========================================
//   ROAD-BASED ROUTE OPTIMIZATION (OSRM)
// ==========================================

async function runAdminRouteOptimization() {
    const busId = document.getElementById('admin-bus-select').value;
    const shift = document.getElementById('admin-shift-select') ? document.getElementById('admin-shift-select').value : 'MORNING';
    state.selectedAdminBus = busId;

    try {
        const res = await fetch(`/api/routes/optimize?bus_id=${busId}&shift=${shift}`);
        const optData = await res.json();

        // Update comparison metrics
        const norm = optData.normal_route || {};
        const opt = optData.optimized_route || {};
        document.getElementById('adm-norm-time').innerText = `${norm.totalTimeMinutes || 0} mins`;
        document.getElementById('adm-norm-dist').innerText = `${norm.totalDistanceKm || 0} km`;
        document.getElementById('adm-opt-time').innerText = `${opt.totalTimeMinutes || 0} mins`;
        document.getElementById('adm-opt-dist').innerText = `${opt.totalDistanceKm || 0} km`;
        document.getElementById('adm-time-saved').innerText = optData.time_saved_minutes > 0 ?
            `⚡ ${optData.time_saved_minutes} mins saved via Dynamic Detour` : 'Direct Path Optimal';

        // Render Flow Nodes
        const flowContainer = document.getElementById('adm-route-nodes-flow');
        const nodes = (opt.pathNodes || []);
        flowContainer.innerHTML = nodes.map((n, i) => {
            const isDetour = n.includes('Detour') || n.includes('avoiding');
            return `
                <span class="node-bubble ${i === 0 || i === nodes.length - 1 ? 'start-end' : ''} ${isDetour ? 'node-detour' : ''}">
                    ${escapeHtml(n)}
                </span>
                ${i < nodes.length - 1 ? '<span class="node-arrow">➔</span>' : ''}
            `;
        }).join('');

        await renderAdminRoadMap(optData);

    } catch (e) {
        console.error('Admin route optimize error:', e);
    }
}

async function renderAdminRoadMap(optData) {
    if (!state.maps.admin) {
        state.maps.admin = L.map('admin-map-container').setView(CAMPUS_COORDS, 12);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '© OpenStreetMap | OSRM Driving Engine'
        }).addTo(state.maps.admin);
    }

    const map = state.maps.admin;

    // Clear previous layers
    state.markers.admin.forEach(m => map.removeLayer(m));
    state.markers.admin = [];
    if (state.polylines.adminNormal) map.removeLayer(state.polylines.adminNormal);
    state.polylines.adminNormal = null;
    if (state.polylines.adminOpt) map.removeLayer(state.polylines.adminOpt);
    state.polylines.adminOpt = null;
    state.polylines.adminHazardSegments.forEach(seg => map.removeLayer(seg));
    state.polylines.adminHazardSegments = [];

    const stops = optData.stops || [];
    const shift = optData.shift || 'MORNING';
    const isEvening = shift === 'EVENING';

    // 1. Render Stop Markers & Campus Marker based on shift sequence
    stops.forEach((st, idx) => {
        const isCampus = st.stop_id === 'CAMPUS' || (st.stop_name || '').toLowerCase().includes('campus');
        if (isCampus) {
            const campusIcon = L.divIcon({
                className: 'custom-stop-div',
                html: `<div class="stop-marker-pin campus-pin">🏫</div>`,
                iconSize: [34, 34],
                iconAnchor: [17, 17]
            });
            const cm = L.marker([st.latitude, st.longitude], { icon: campusIcon }).addTo(map);
            cm.bindPopup(`<b>🏫 Kalvi College Campus (Depot)</b><br>${isEvening ? 'Shift Origin (Departs 05:00 PM)' : 'Final Campus Destination (Arrives 08:30 AM)'}`);
            state.markers.admin.push(cm);
        } else {
            let displayNum = st.stop_order;
            if (!displayNum || displayNum > 10 || displayNum < 1) {
                displayNum = isEvening ? (stops.length - idx) : (idx + 1);
            }
            const icon = L.divIcon({
                className: 'custom-stop-div',
                html: `<div class="stop-marker-pin normal-pin">${displayNum}</div>`,
                iconSize: [28, 28],
                iconAnchor: [14, 14]
            });
            const m = L.marker([st.latitude, st.longitude], { icon: icon }).addTo(map);
            m.bindPopup(`
                <div style="font-size:13px;">
                    <strong>Stop #${displayNum}: ${escapeHtml(st.stop_name)}</strong><br>
                    <span>${isEvening ? 'Evening Drop: <strong>' + st.evening_time + '</strong>' : 'Morning Pickup: <strong>' + st.morning_time + '</strong>'}</span><br>
                    <span class="badge badge-info mt-1">${st.student_count || 0} Registered Students</span>
                </div>
            `);
            state.markers.admin.push(m);
        }
    });

    // 2. Render Normal Route in BLUE
    // Normal route is ALWAYS visible
    const normalGeo = optData.normal_geometry || [];
    if (normalGeo.length > 1) {
        state.polylines.adminNormal = L.polyline(normalGeo, {
            color: '#2563eb', // Solid vibrant BLUE
            weight: 5,
            opacity: 0.8
        }).addTo(map);

        state.polylines.adminNormal.bindPopup(`
            <div style="font-size:13px;">
                <strong style="color:#2563eb;">🟦 Normal Bus Route (${shift})</strong><br>
                <span>Follows OpenStreetMap road corridor without detours.</span><br>
                <span>Estimated Time: <strong>${optData.normal_route?.totalTimeMinutes || 0} mins</strong> (${optData.normal_route?.totalDistanceKm || 0} km)</span>
            </div>
        `);
    }

    // 3. Render Optimized Detour Route in RED when an issue is active
    // BOTH ROUTES REMAIN VISIBLE AT THE SAME TIME!
    if (optData.has_detour && optData.optimized_geometry && optData.optimized_geometry.length > 1) {
        state.polylines.adminOpt = L.polyline(optData.optimized_geometry, {
            color: '#ef4444', // Highlighted RED
            weight: 6,
            opacity: 0.95,
            dashArray: '10, 6'
        }).addTo(map);

        state.polylines.adminOpt.bindPopup(`
            <div style="font-size:13px;">
                <strong style="color:#ef4444;">🟥 Dynamically Optimized Detour Route</strong><br>
                <span>Active detour avoiding simulated hazards/roadblocks on corridor.</span><br>
                <span>Delay Prevented: <strong style="color:#10b981;">${optData.time_saved_minutes} mins saved</strong></span><br>
                <span>New ETA: <strong>${optData.optimized_route?.totalTimeMinutes || 0} mins</strong> (${optData.optimized_route?.totalDistanceKm || 0} km)</span>
            </div>
        `);
    }

    // 4. Highlight Affected Road Segments & Hazard Icons (Positioned directly on the blocked road)
    const legs = optData.legs || [];
    for (const leg of legs) {
        if (leg.isAffected) {
            // Hazard is on the direct road between consecutive stops
            const hazardLat = (leg.fromLat + leg.toLat) / 2;
            const hazardLng = (leg.fromLng + leg.toLng) / 2;

            // Hazard badge marker
            const alertIcon = L.divIcon({
                className: 'custom-stop-div',
                html: `<div style="font-size:24px; filter:drop-shadow(0 0 6px ${leg.colorCode || '#ef4444'}); cursor:pointer;" title="${escapeHtml(leg.conditionType)}">${leg.icon || '⚠️'}</div>`,
                iconSize: [30, 30],
                iconAnchor: [15, 15]
            });
            const am = L.marker([hazardLat, hazardLng], { icon: alertIcon }).addTo(map);
            am.bindPopup(`
                <div style="font-size:13px;">
                    <strong>${leg.icon || '⚠️'} ${escapeHtml(leg.conditionType || 'Active Hazard')}</strong><br>
                    <span>Direct road: <b>${escapeHtml(leg.fromStop)}</b> ➔ <b>${escapeHtml(leg.toStop)}</b></span><br>
                    <span style="color:#ef4444; font-weight:600;">🚫 Blocked road barred from traversal.</span><br>
                    <span style="color:#10b981; font-weight:600;">✅ Bus reaches <b>${escapeHtml(leg.toStop)}</b> via alternative smallest route.</span>
                </div>
            `);
            state.markers.admin.push(am);
        }
    }

    if (state.polylines.adminNormal) {
        if (state.polylines.adminOpt) {
            const group = new L.featureGroup([state.polylines.adminNormal, state.polylines.adminOpt]);
            map.fitBounds(group.getBounds(), { padding: [35, 35] });
        } else {
            map.fitBounds(state.polylines.adminNormal.getBounds(), { padding: [35, 35] });
        }
    }
}

// Global in-memory cache for OSRM queries
const osrmGeometryCache = new Map();

async function fetchOsrmRoadGeometry(points) {
    if (!points || points.length < 2) return points;

    // Build OSRM query string: lon,lat;lon,lat;...
    const coordString = points.map(pt => `${pt[1].toFixed(5)},${pt[0].toFixed(5)}`).join(';');
    if (osrmGeometryCache.has(coordString)) {
        return osrmGeometryCache.get(coordString);
    }

    const url = `https://router.project-osrm.org/route/v1/driving/${coordString}?overview=full&geometries=geojson&steps=false`;

    try {
        const res = await fetch(url);
        if (res.ok) {
            const data = await res.json();
            if (data.code === 'Ok' && data.routes && data.routes.length > 0) {
                const coords = data.routes[0].geometry.coordinates;
                const result = coords.map(c => [c[1], c[0]]);
                osrmGeometryCache.set(coordString, result);
                return result;
            }
        }
    } catch (e) {
        console.warn('OSRM public API rate limit/offline fallback:', e);
    }

    // Fallback: Generate realistic road curve points following road bends (Never straight lines!)
    const fallback = generateCurvedRoadGeometry(points);
    return fallback;
}

function generateCurvedRoadGeometry(points) {
    const curved = [];
    for (let i = 0; i < points.length - 1; i++) {
        const p1 = points[i];
        const p2 = points[i + 1];

        curved.push(p1);

        // Add 3 intermediate curved waypoints mimicking real street turns
        const midLat = (p1[0] + p2[0]) / 2;
        const midLng = (p1[1] + p2[1]) / 2;

        const offset = 0.0018 * Math.sin(i * 1.5 + 0.8);
        curved.push([p1[0] * 0.75 + p2[0] * 0.25 + offset, p1[1] * 0.75 + p2[1] * 0.25 - offset]);
        curved.push([midLat - offset * 0.5, midLng + offset * 0.5]);
        curved.push([p1[0] * 0.25 + p2[0] * 0.75 + offset, p1[1] * 0.25 + p2[1] * 0.75 + offset]);
    }
    curved.push(points[points.length - 1]);
    return curved;
}

// ==========================================
//          STOPS & INCIDENTS API
// ==========================================

async function fetchStopsAndBuses() {
    try {
        const [stopsRes, busesRes] = await Promise.all([
            fetch('/api/admin/stops').then(r => r.json()),
            fetch('/api/admin/buses').then(r => r.json())
        ]);
        state.allStops = stopsRes;

        const tbody = document.getElementById('all-stops-tbody');
        tbody.innerHTML = stopsRes.map(st => `
            <tr>
                <td><strong>${escapeHtml(st.stop_name)}</strong></td>
                <td><span class="badge badge-primary">${st.bus_route}</span></td>
                <td><code>${st.latitude.toFixed(4)}, ${st.longitude.toFixed(4)}</code></td>
                <td>${st.morning_time}</td>
                <td><span class="badge badge-info">${st.student_count || 0}</span></td>
            </tr>
        `).join('');

        setupStopDropdowns();
        setupIncidentDropdowns(stopsRes);
    } catch (e) {
        console.error('Error fetching stops:', e);
    }
}

async function handleAddStop(e) {
    e.preventDefault();
    const name = document.getElementById('new-stop-name').value.trim();
    const bus = document.getElementById('new-stop-bus').value;
    const lat = document.getElementById('new-stop-lat').value;
    const lng = document.getElementById('new-stop-lng').value;
    const mTime = document.getElementById('new-stop-mtime').value;
    const eTime = document.getElementById('new-stop-etime').value;

    try {
        const res = await fetch('/api/admin/add-stop', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                stop_name: name,
                bus_route: bus,
                latitude: lat,
                longitude: lng,
                morning_time: mTime,
                evening_time: eTime
            })
        });

        if (res.ok) {
            showToast(`Stop "${name}" added to ${bus}! Route re-optimized.`, 'success');
            document.getElementById('add-stop-form').reset();
            await fetchStopsAndBuses();
            await fetchAllStopsForDropdowns();
            runAdminRouteOptimization();
            loadDriverBusRoute();
        } else {
            showToast('Failed to add stop.', 'error');
        }
    } catch (err) {
        showToast('Error communicating with backend.', 'error');
    }
}

async function fetchIncidents() {
    try {
        const res = await fetch('/api/admin/incidents');
        const list = await res.json();

        const tbody = document.getElementById('incidents-tbody');
        if (list.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">No road hazards recorded.</td></tr>';
            return;
        }

        tbody.innerHTML = list.map(inc => `
            <tr>
                <td><span class="badge ${inc.active === 1 ? 'badge-danger' : 'badge-outline'}">${inc.incident_type}</span></td>
                <td><strong>${escapeHtml(inc.road_source)}</strong> ➔ <strong>${escapeHtml(inc.road_destination)}</strong></td>
                <td>${escapeHtml(inc.description)}</td>
                <td>${inc.active === 1 ? '<span class="badge badge-warning">Active Detour</span>' : '<span class="badge badge-success">Resolved</span>'}</td>
                <td>
                    <button class="btn btn-sm ${inc.active === 1 ? 'btn-outline' : 'btn-warning'}" onclick="toggleIncident(${inc.id}, ${inc.active === 1 ? 0 : 1})">
                        ${inc.active === 1 ? 'Resolve' : 'Reactivate'}
                    </button>
                </td>
            </tr>
        `).join('');
    } catch (e) {
        console.error('Error fetching incidents:', e);
    }
}

async function toggleIncident(id, active) {
    try {
        const res = await fetch('/api/admin/toggle-incident', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ id: id, active: active })
        });

        if (res.ok) {
            showToast(`Hazard updated. Recalculating routes in real-time...`, 'info');
            fetchIncidents();
            fetchAdminStats();
            runAdminRouteOptimization();
            loadDriverBusRoute();
        }
    } catch (e) {
        showToast('Error updating incident.', 'error');
    }
}

async function handleAddIncident(e) {
    e.preventDefault();
    const src = document.getElementById('inc-source').value;
    const dst = document.getElementById('inc-dest').value;
    const type = document.getElementById('inc-type').value;
    const penalty = document.getElementById('inc-penalty').value;
    const desc = document.getElementById('inc-desc').value.trim();

    try {
        const res = await fetch('/api/admin/add-incident', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                road_source: src,
                road_destination: dst,
                incident_type: type,
                penalty_mins: penalty,
                description: desc
            })
        });

        if (res.ok) {
            showToast('Incident registered! Detour active in real-time.', 'warning');
            document.getElementById('add-incident-form').reset();
            fetchIncidents();
            fetchAdminStats();
            runAdminRouteOptimization();
            loadDriverBusRoute();
        }
    } catch (e) {
        showToast('Error broadcasting hazard.', 'error');
    }
}

// ==========================================
//          HELPERS & INITIAL DATA
// ==========================================

async function loadInitialData() {
    try {
        const res = await fetch('/api/admin/stops');
        state.allStops = await res.json();
        setupStopDropdowns();
    } catch (e) {}
}

async function fetchAllStopsForDropdowns() {
    try {
        const res = await fetch('/api/admin/stops');
        if (res.ok) {
            state.allStops = await res.json();
            setupStopDropdowns();
        }
    } catch (e) {
        console.error('Error updating stop dropdowns:', e);
    }
}

function setupStopDropdowns() {
    // 1. Student Registration modal dropdown
    const regSelect = document.getElementById('reg-stop');
    if (regSelect) {
        const currentVal = regSelect.value;
        regSelect.innerHTML = '<option value="">-- Select Stopping --</option>';
        state.allStops.forEach(st => {
            if (!st.stop_name.includes('Campus')) {
                const opt = document.createElement('option');
                opt.value = st.stop_name;
                opt.innerText = `${st.stop_name} (Bus ${st.bus_route} - Morning: ${st.morning_time} | Evening: ${st.evening_time})`;
                regSelect.appendChild(opt);
            }
        });
        if (currentVal) regSelect.value = currentVal;
    }

    // 2. Student Portal Dashboard stop selection dropdown
    const studentSelect = document.getElementById('student-stop-select');
    if (studentSelect) {
        const currentVal = studentSelect.value;
        studentSelect.innerHTML = '<option value="">-- Select Boarding Stop --</option>';
        state.allStops.forEach(st => {
            if (!st.stop_name.includes('Campus')) {
                const opt = document.createElement('option');
                opt.value = st.stop_name;
                opt.innerText = `${st.stop_name} (Bus ${st.bus_route} - Morning: ${st.morning_time} | Evening: ${st.evening_time})`;
                studentSelect.appendChild(opt);
            }
        });
        if (currentVal) studentSelect.value = currentVal;
    }
}

function setupIncidentDropdowns(stops) {
    const srcSelect = document.getElementById('inc-source');
    const dstSelect = document.getElementById('inc-dest');
    if (!srcSelect || !dstSelect) return;

    srcSelect.innerHTML = '';
    dstSelect.innerHTML = '';

    stops.forEach(st => {
        const opt1 = document.createElement('option');
        opt1.value = st.stop_name;
        opt1.innerText = st.stop_name;
        srcSelect.appendChild(opt1);

        const opt2 = document.createElement('option');
        opt2.value = st.stop_name;
        opt2.innerText = st.stop_name;
        dstSelect.appendChild(opt2);
    });

    if (dstSelect.options.length > 1) dstSelect.selectedIndex = 1;
}

function openKeyModal() {
    document.getElementById('modal-api-key').classList.add('active');
}

function closeKeyModal() {
    document.getElementById('modal-api-key').classList.remove('active');
}

function showToast(message, type = 'info') {
    const toast = document.getElementById('toast');
    toast.innerText = message;
    toast.className = `toast toast-${type} show`;
    setTimeout(() => {
        toast.className = 'toast';
    }, 4000);
}

function escapeHtml(text) {
    if (!text) return '';
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return text.toString().replace(/[&<>"']/g, m => map[m]);
}
