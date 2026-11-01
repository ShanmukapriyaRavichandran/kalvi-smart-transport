// Kalvi College Transport Management System - Admin Dashboard Logic

let allStudentsCache = [];
let allStopsCache = [];
let latestRoutePlan = null;

const B01_ERODE_STOPS = [
  "Erode Bus Stand", "Brough Road", "GH Roundana", "Paneerselvam Park",
  "Karungalpalayam", "Pallipalayam", "Kumarapalayam", "Bhavani Old Bus Stand",
  "Kalingarayanpalayam", "Chithode Junction"
];

const B02_ERODE_STOPS = [
  "Solar Pirivu", "Lakkapuram", "Kollampalayam Bypass", "Railway Station Gate",
  "Marapalam", "Surampatti Valasu", "Thindal Murugan Temple", "Villarasampatti",
  "Perundurai Old Bus Stand", "SIPCOT Perundurai Gate"
];

const B03_ERODE_STOPS = [
  "Modakkurichi Town", "Elumathur Junction", "Sivagiri Bus Stop", "Kodumudi Road Pirivu",
  "Muthur Corner", "Chennimalai Bus Stand", "Ingur Industrial Area", "Vijayamangalam Toll",
  "Uthukuli Road", "Nasiyanur Junction"
];

// Tab navigation (Attendance module removed; Student Registration added)
function switchAdminTab(tab) {
  const tabs = ['dashboard', 'students', 'registration', 'routes', 'fleet', 'fees', 'unallocated'];
  tabs.forEach(t => {
    const btn = document.getElementById(`tab-${t}`);
    const sec = document.getElementById(`sec-${t}`);
    if (btn && sec) {
      if (t === tab) {
        btn.className = 'admin-tab-btn active px-3.5 py-2 text-sm font-semibold rounded-lg text-brand-700 bg-brand-50 flex items-center space-x-2 whitespace-nowrap';
        sec.classList.remove('hidden');
      } else {
        btn.className = 'admin-tab-btn px-3.5 py-2 text-sm font-semibold rounded-lg text-slate-600 hover:text-slate-900 hover:bg-slate-100 flex items-center space-x-2 whitespace-nowrap';
        sec.classList.add('hidden');
      }
    }
  });

  if (tab === 'dashboard') loadAdminDashboard();
  else if (tab === 'students') loadStudentsDirectory();
  else if (tab === 'registration') loadRegistrationModule();
  else if (tab === 'routes') { if (!latestRoutePlan) triggerRunRoutePlan(); }
  else if (tab === 'fleet') loadFleetCards();
  else if (tab === 'fees') loadFeeReports();
  else if (tab === 'unallocated') loadUnallocated();
}

// 1. DASHBOARD OVERVIEW
async function loadAdminDashboard() {
  try {
    const res = await fetch('/api/dashboard/stats');
    const stats = await res.json();

    document.getElementById('stat-total-students').innerText = stats.total_students;
    document.getElementById('stat-present-students').innerText = stats.present_students;
    const presentPct = Math.round((stats.present_students / (stats.total_students || 1)) * 100);
    document.getElementById('stat-present-pct').innerText = `${presentPct}% present today`;

    document.getElementById('stat-authorized-students').innerText = stats.authorized_students;
    document.getElementById('stat-boarded-students').innerText = stats.boarded_students;
    document.getElementById('stat-capacity-display').innerText = `out of ${stats.total_capacity} seats`;

    document.getElementById('stat-fees-collected').innerText = `₹${stats.total_fees_collected.toLocaleString('en-IN')}`;
    document.getElementById('stat-paid-count').innerText = `${stats.paid_count} Students Paid`;

    document.getElementById('stat-fees-pending').innerText = `₹${stats.total_fees_pending.toLocaleString('en-IN')}`;
    document.getElementById('stat-unpaid-count').innerText = `${stats.unpaid_count} Pending`;

    // Buses in dashboard (Explicitly Mini Bus and Standard Bus)
    const busRes = await fetch('/api/buses');
    const buses = await busRes.json();
    const container = document.getElementById('dashboard-buses-container');
    container.innerHTML = '';

    buses.forEach(b => {
      const pct = Math.round(((b.boarded_count || 0) / b.capacity) * 100);
      const isBreakdown = b.status === 'breakdown';
      const isFull = (b.boarded_count || 0) >= b.capacity;
      const card = document.createElement('div');
      card.className = `p-3.5 rounded-xl border ${isBreakdown ? 'bg-rose-50 border-rose-200' : 'bg-slate-50 border-slate-200'} space-y-2`;
      card.innerHTML = `
        <div class="flex items-center justify-between text-xs">
          <div class="flex items-center space-x-2">
            <span class="font-bold text-slate-900">${b.bus_id} (${b.bus_type})</span>
            ${isBreakdown 
              ? '<span class="px-2 py-0.5 rounded text-[10px] bg-rose-200 text-rose-800 font-bold">BREAKDOWN</span>' 
              : '<span class="px-2 py-0.5 rounded text-[10px] bg-emerald-100 text-emerald-800 font-bold uppercase tracking-wider">ACTIVE</span>'
            }
          </div>
          <span class="font-mono text-slate-800 font-bold text-xs">${b.boarded_count || 0} / ${b.capacity} Seats</span>
        </div>
        <div class="w-full bg-slate-200 rounded-full h-2.5 overflow-hidden">
          <div class="${isBreakdown ? 'bg-rose-500' : isFull ? 'bg-amber-500' : 'bg-blue-600'} h-2.5 rounded-full transition-all duration-500" style="width: ${Math.min(pct, 100)}%"></div>
        </div>
        <div class="flex justify-between text-[11px] text-slate-500">
          <span>Driver: <strong class="text-slate-700">${b.driver_name}</strong></span>
          <span class="${isFull ? 'text-amber-700 font-bold' : 'text-slate-600 font-medium'}">${b.capacity - (b.boarded_count || 0)} Available</span>
        </div>
      `;
      container.appendChild(card);
    });

  } catch (err) {
    console.error('Error loading dashboard stats:', err);
  }
}

// 2. STUDENTS DIRECTORY (WITH PROMINENT ROLL NO SEARCH)
async function loadStudentsDirectory() {
  try {
    const [stRes, stopsRes] = await Promise.all([
      fetch('/api/students'),
      fetch('/api/stops')
    ]);
    allStudentsCache = await stRes.json();
    allStopsCache = await stopsRes.json();

    populateStopsDropdown(allStopsCache);
    renderStudentsTable(allStudentsCache);
  } catch (err) {
    console.error('Error fetching students:', err);
  }
}

function populateStopsDropdown(stops) {
  const select = document.getElementById('filter-stop-select');
  if (!select) return;
  const currentVal = select.value;
  select.innerHTML = '<option value="">All Erode Stops (30 Stops)</option>';
  
  // Group stops by bus route
  const b01Group = document.createElement('optgroup');
  b01Group.label = 'Bus B01 Stops (Mini Bus)';
  const b02Group = document.createElement('optgroup');
  b02Group.label = 'Bus B02 Stops (Standard Bus)';
  const b03Group = document.createElement('optgroup');
  b03Group.label = 'Bus B03 Stops (Standard Bus)';

  stops.forEach(s => {
    const opt = document.createElement('option');
    opt.value = s.stop_id;
    opt.innerText = s.stop_name;
    if (s.bus_route === 'B01') b01Group.appendChild(opt);
    else if (s.bus_route === 'B02') b02Group.appendChild(opt);
    else b03Group.appendChild(opt);
  });

  select.appendChild(b01Group);
  select.appendChild(b02Group);
  select.appendChild(b03Group);
  select.value = currentVal;
}

// Prominent Search by Roll Number
function filterStudentsByRollNo() {
  const query = (document.getElementById('student-search-rollno').value || '').trim().toUpperCase();
  const bus = document.getElementById('filter-bus-select').value;
  const stop = document.getElementById('filter-stop-select').value;
  const paid = document.getElementById('filter-paid-select').value;
  const present = document.getElementById('filter-present-select').value;

  const filtered = allStudentsCache.filter(s => {
    const matchesRoll = !query || s.student_id.toUpperCase().includes(query) || s.name.toUpperCase().includes(query);
    const matchesBus = !bus || (s.assigned_bus_id && s.assigned_bus_id.toUpperCase() === bus.toUpperCase());
    const matchesStop = !stop || s.stop_id === stop;
    const matchesPaid = paid === '' || String(s.paid) === paid;
    const matchesPresent = present === '' || String(s.present) === present;
    return matchesRoll && matchesBus && matchesStop && matchesPaid && matchesPresent;
  });

  const countLabel = document.getElementById('students-search-count-label');
  if (countLabel) {
    if (query) {
      countLabel.innerText = `Showing ${filtered.length} match(es) for Roll No '${query}'`;
    } else {
      countLabel.innerText = `Showing ${filtered.length} of ${allStudentsCache.length} students`;
    }
  }

  renderStudentsTable(filtered);
}

function clearStudentRollNoSearch() {
  const searchInput = document.getElementById('student-search-rollno');
  if (searchInput) searchInput.value = '';
  document.getElementById('filter-bus-select').value = '';
  document.getElementById('filter-stop-select').value = '';
  document.getElementById('filter-paid-select').value = '';
  document.getElementById('filter-present-select').value = '';
  filterStudentsByRollNo();
}

function filterStudentsTable() {
  filterStudentsByRollNo();
}

function renderStudentsTable(students) {
  const tbody = document.getElementById('students-table-body');
  tbody.innerHTML = '';

  if (students.length === 0) {
    tbody.innerHTML = `<tr><td colspan="9" class="text-center py-8 text-slate-400 font-medium">No students match the selected search or filter criteria.</td></tr>`;
    return;
  }

  students.forEach(s => {
    const tr = document.createElement('tr');
    tr.className = 'hover:bg-slate-50 transition';

    const presentBadge = s.present == 1 
      ? `<span class="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800">Present</span>`
      : `<span class="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-slate-100 text-slate-600">Absent</span>`;

    const feeBadge = s.paid == 1
      ? `<span class="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800">Paid ₹2,500</span>`
      : `<span class="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-rose-100 text-rose-800">Pending</span>`;

    const authBadge = s.authorized == 1
      ? `<span class="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-blue-100 text-blue-800">Authorized</span>`
      : `<span class="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-amber-100 text-amber-800">Fee Required</span>`;

    const boardBadge = s.boarded == 1
      ? `<span class="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-purple-100 text-purple-800 font-mono">${s.assigned_bus_id || 'Boarded'}</span>`
      : `<span class="text-slate-400 text-[11px] font-medium">Waiting</span>`;

    const busTypeStr = s.assigned_bus_id === 'B01' ? 'Mini Bus' : 'Standard Bus';

    tr.innerHTML = `
      <td class="py-2.5 px-4 font-mono font-bold text-slate-900">${s.student_id}</td>
      <td class="py-2.5 px-4 font-semibold text-slate-800">${s.name}</td>
      <td class="py-2.5 px-4 text-slate-600">${s.department || 'Computer Science (CSE)'}</td>
      <td class="py-2.5 px-4 text-center">
        <span class="px-2 py-0.5 rounded font-mono font-bold text-[11px] bg-slate-100 text-slate-800 border border-slate-200" title="${busTypeStr}">
          ${s.assigned_bus_id || 'B01'}
        </span>
      </td>
      <td class="py-2.5 px-4 font-semibold text-slate-700">${s.stop_id}</td>
      <td class="py-2.5 px-4 text-center">${presentBadge}</td>
      <td class="py-2.5 px-4 text-center">${feeBadge}</td>
      <td class="py-2.5 px-4 text-center">${authBadge}</td>
      <td class="py-2.5 px-4 text-center">${boardBadge}</td>
    `;
    tbody.appendChild(tr);
  });
}

// 3. STUDENT REGISTRATION MODULE (ADMIN CAN SIGNUP NEW STUDENTS)
function loadRegistrationModule() {
  onBusSelectForRegistration();
  autoSuggestRollNumber();
  updateRegPasswordPreview();
}

function onBusSelectForRegistration() {
  const busSelect = document.getElementById('reg-bus-id');
  const stopSelect = document.getElementById('reg-stop-id');
  if (!busSelect || !stopSelect) return;

  const busId = busSelect.value;
  stopSelect.innerHTML = '';

  let stops = B01_ERODE_STOPS;
  if (busId === 'B02') stops = B02_ERODE_STOPS;
  else if (busId === 'B03') stops = B03_ERODE_STOPS;

  stops.forEach((stop, idx) => {
    const opt = document.createElement('option');
    opt.value = stop;
    opt.innerText = `${idx + 1}. ${stop}`;
    stopSelect.appendChild(opt);
  });

  updateRegPasswordPreview();
}

function autoSuggestRollNumber() {
  const dept = document.getElementById('reg-department').value;
  const rollInput = document.getElementById('reg-roll-no');
  if (!rollInput) return;

  let prefix = '25CSR';
  if (dept.includes('Information')) prefix = '25ITR';
  else if (dept.includes('Electronics & Communication')) prefix = '25ECR';
  else if (dept.includes('Mechanical')) prefix = '25MER';
  else if (dept.includes('AI & Data')) prefix = '25ADR';
  else if (dept.includes('Civil')) prefix = '25CER';
  else if (dept.includes('Electrical & Electronics')) prefix = '25EER';

  // Find next number in cache if exists
  const existingMatches = allStudentsCache.filter(s => s.student_id.startsWith(prefix));
  let nextNum = 201 + existingMatches.length;
  if (rollInput.value.trim() === '' || rollInput.value.startsWith('25')) {
    rollInput.value = `${prefix}${nextNum}`;
  }
  updateRegPasswordPreview();
}

function updateRegPasswordPreview() {
  const rollNo = (document.getElementById('reg-roll-no').value || '25CSR265').trim().toUpperCase();
  const busId = document.getElementById('reg-bus-id').value;
  const preview = document.getElementById('reg-password-preview');
  if (preview) {
    preview.innerText = `${rollNo}@${busId}`;
  }
}

async function handleStudentRegistration(e) {
  e.preventDefault();
  const name = document.getElementById('reg-student-name').value.trim();
  const dept = document.getElementById('reg-department').value;
  const roll_no = document.getElementById('reg-roll-no').value.trim().toUpperCase();
  const bus_id = document.getElementById('reg-bus-id').value;
  const stop_id = document.getElementById('reg-stop-id').value;
  const semester = document.getElementById('reg-semester').value;
  const fee_paid = document.getElementById('reg-fee-paid').value;

  if (!name || !roll_no) {
    alert('Please enter student name and roll number.');
    return;
  }

  try {
    const res = await fetch('/api/admin/register-student', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name,
        department: dept,
        roll_no,
        bus_id,
        stop_id,
        semester,
        fee_paid: parseInt(fee_paid)
      })
    });

    const data = await res.json();
    const card = document.getElementById('reg-success-card');

    if (data.success) {
      card.className = 'p-5 rounded-2xl bg-emerald-50 border border-emerald-200 text-xs text-emerald-950 space-y-2 block shadow-sm';
      card.innerHTML = `
        <div class="flex items-center space-x-2 text-emerald-800 font-bold text-sm">
          <i class="fa-solid fa-circle-check text-emerald-600"></i>
          <span>Student Registration Completed Successfully!</span>
        </div>
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-2 text-xs">
          <div class="p-2.5 bg-white rounded-xl border border-emerald-100">
            <span class="text-slate-500 text-[10px] uppercase font-bold block">User ID (Roll No)</span>
            <span class="font-mono font-bold text-slate-900">${data.student.student_id}</span>
          </div>
          <div class="p-2.5 bg-white rounded-xl border border-emerald-100">
            <span class="text-slate-500 text-[10px] uppercase font-bold block">Login Password</span>
            <span class="font-mono font-bold text-emerald-700">${data.student.password}</span>
          </div>
          <div class="p-2.5 bg-white rounded-xl border border-emerald-100">
            <span class="text-slate-500 text-[10px] uppercase font-bold block">Assigned Bus</span>
            <span class="font-bold text-slate-900">${data.student.bus_id} (${data.student.bus_id === 'B01' ? 'Mini Bus' : 'Standard Bus'})</span>
          </div>
          <div class="p-2.5 bg-white rounded-xl border border-emerald-100">
            <span class="text-slate-500 text-[10px] uppercase font-bold block">Pickup Stop</span>
            <span class="font-bold text-slate-900 truncate block">${data.student.stop_id}</span>
          </div>
        </div>
        <p class="text-[11px] text-emerald-800 mt-2">
          The student can now log in using <strong>Roll No: ${data.student.student_id}</strong> and <strong>Password: ${data.student.password}</strong>.
        </p>
      `;

      // Refresh cached students directory
      loadStudentsDirectory();
      loadAdminDashboard();
      // Reset form fields
      document.getElementById('reg-student-name').value = '';
      autoSuggestRollNumber();
    } else {
      card.className = 'p-4 rounded-xl bg-rose-50 border border-rose-200 text-xs text-rose-900 space-y-1 block';
      card.innerHTML = `<strong>Registration Failed:</strong> ${data.message || 'Could not register student.'}`;
    }
  } catch (err) {
    alert('Registration request failed: ' + err.message);
  }
}

function resetRegistrationForm() {
  setTimeout(() => {
    onBusSelectForRegistration();
    autoSuggestRollNumber();
  }, 50);
}

// 4. DIJKSTRA ROUTE PLANNING
async function triggerRunRoutePlan() {
  const btn = document.getElementById('btn-run-plan');
  if (btn) btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i><span>Optimizing Shortest Paths...</span>';

  try {
    const res = await fetch('/api/routes/plan', { method: 'POST' });
    const data = await res.json();
    if (data.success) {
      latestRoutePlan = data.plan;
      renderRoutePlans(data.plan);
      loadAdminDashboard();
    }
  } catch (err) {
    alert('Route planning failed: ' + err.message);
  } finally {
    if (btn) btn.innerHTML = '<i class="fa-solid fa-play"></i><span>Generate / Refresh Route Plans</span>';
  }
}

function renderRoutePlans(plan) {
  const container = document.getElementById('route-plans-container');
  container.innerHTML = '';

  const summary = document.createElement('div');
  summary.className = 'p-4 rounded-xl bg-slate-900 text-white flex flex-col sm:flex-row items-center justify-between gap-3 text-xs';
  summary.innerHTML = `
    <div>
      <span class="font-bold text-amber-400 uppercase tracking-wider text-[11px]">Dijkstra Execution Complete</span>
      <div class="text-sm font-black mt-0.5">${plan.total_boarded_count} Students Boarded Across ${plan.bus_plans.length} Buses</div>
    </div>
    <div class="flex items-center space-x-3 text-[11px] mono-font">
      <span class="px-2.5 py-1 rounded bg-slate-800 text-slate-300">Total Transit Time: <strong>${plan.total_system_travel_time} Mins</strong></span>
      <span class="px-2.5 py-1 rounded bg-slate-800 text-emerald-400">Total Bus Mileage: <strong>${plan.total_boarded_count} Passengers Safe</strong></span>
    </div>
  `;
  container.appendChild(summary);

  const logicPanel = document.createElement('div');
  logicPanel.className = 'p-4 rounded-xl border border-blue-200 bg-blue-50 text-xs text-blue-950 space-y-2';
  logicPanel.innerHTML = `
    <div class="font-black text-sm">How the live route decision works</div>
    <div>Registered students read from database: <strong>${plan.registered_student_count ?? 0}</strong> &nbsp;|&nbsp;
      Fee-paid students: <strong>${plan.fee_paid_student_count ?? 0}</strong> &nbsp;|&nbsp;
      Fee-paid and present today: <strong>${plan.eligible_present_count ?? 0}</strong></div>
    <ol class="list-decimal pl-5 space-y-1">
      <li>If a fee-paid student is marked PRESENT at a stop, that stop stays in the bus pickup route.</li>
      <li>If the fee-paid student at a stop is marked ABSENT, the bus skips the pickup there. Dijkstra calculates the shortest road path to the next stop that needs a pickup.</li>
      <li>The driver sees the actual path, travel time, and skipped-stop reason below. No students are generated by the route planner.</li>
    </ol>
    <div class="font-bold">Absent pickup stops skipped this run: ${plan.skipped_absent_stop_count ?? 0}</div>
  `;
  container.appendChild(logicPanel);

  plan.bus_plans.forEach(bp => {
    const card = document.createElement('div');
    card.className = 'p-5 rounded-2xl border border-slate-200 bg-white shadow-sm space-y-4';

    const routeStopsPills = bp.route.map((s, idx) => `
      <span class="inline-flex items-center space-x-1 px-2.5 py-1 rounded-lg bg-slate-100 text-slate-800 text-xs font-semibold">
        <span class="w-4 h-4 rounded-full bg-slate-300 text-slate-700 flex items-center justify-center text-[10px] font-bold">${idx + 1}</span>
        <span>${s}</span>
      </span>
    `).join('<i class="fa-solid fa-arrow-right text-[10px] text-slate-400"></i>');

    const busTypeStr = bp.bus_id === 'B01' ? 'Mini Bus' : 'Standard Bus';

    card.innerHTML = `
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-100 pb-3">
        <div class="flex items-center space-x-3">
          <div class="w-10 h-10 rounded-xl bg-gradient-to-tr from-amber-500 to-amber-300 flex items-center justify-center text-slate-900 font-bold">
            <i class="fa-solid fa-bus"></i>
          </div>
          <div>
            <h4 class="font-bold text-slate-900 text-sm">${bp.bus_id} (${busTypeStr}) &bull; Driver: ${bp.driver_name}</h4>
            <p class="text-[11px] text-slate-500">Boarded: <strong class="text-slate-800">${bp.boarded_students.length} / ${bp.capacity} Seats</strong> (${bp.capacity - bp.boarded_students.length} Seats Available)</p>
          </div>
        </div>
        <div class="text-right">
          <span class="px-3 py-1 rounded-full text-xs font-bold ${bp.status === 'breakdown' ? 'bg-rose-100 text-rose-800' : 'bg-emerald-100 text-emerald-800'}">
            ${bp.status.toUpperCase()}
          </span>
          <div class="text-[11px] text-slate-400 mt-1">Route Duration: ~${bp.trip_travel_time} mins</div>
        </div>
      </div>

      <div class="space-y-1.5">
        <span class="text-[11px] font-bold text-slate-400 uppercase tracking-wider">Shortest Path Sequence (CAMPUS &rarr; Stops &rarr; CAMPUS):</span>
        <div class="flex flex-wrap items-center gap-2 pt-1">
          <span class="px-2.5 py-1 rounded-lg bg-amber-100 text-amber-900 text-xs font-bold">CAMPUS</span>
          <i class="fa-solid fa-arrow-right text-[10px] text-slate-400"></i>
          ${routeStopsPills || '<span class="text-xs text-slate-400">No intermediate pickups needed today</span>'}
          <i class="fa-solid fa-arrow-right text-[10px] text-slate-400"></i>
          <span class="px-2.5 py-1 rounded-lg bg-amber-100 text-amber-900 text-xs font-bold">CAMPUS</span>
        </div>
      </div>

      <div class="pt-3 border-t border-slate-100 space-y-3">
        <details class="text-xs" open>
          <summary class="cursor-pointer font-semibold text-blue-700 hover:text-blue-900">Dijkstra execution log — actual road paths and pickup decisions</summary>
          <div class="mt-2 space-y-2">
            ${(bp.route_legs || []).map(leg => `
              <div class="p-2.5 rounded-lg border border-slate-200 bg-slate-50">
                <div class="font-bold text-slate-800">${leg.from} → ${leg.to} <span class="text-amber-700">(${leg.time == null ? 'No path' : leg.time + ' min'})</span></div>
                <div class="text-slate-600 mt-0.5">Road path: ${(leg.path || []).join(' → ') || 'No connected path found'}</div>
                <div class="text-[10px] text-slate-400 mt-0.5">${leg.reason || 'Dijkstra shortest path'}</div>
              </div>
            `).join('') || '<div class="text-slate-500">No pickup required for this bus today.</div>'}
          </div>
          <div class="mt-3 space-y-1.5">
            ${(bp.route_decisions || []).map(decision => `
              <div class="p-2 rounded-lg ${decision.status === 'SKIPPED_ABSENT' ? 'bg-amber-50 border border-amber-200 text-amber-900' : decision.status === 'PICKUP' ? 'bg-emerald-50 border border-emerald-200 text-emerald-900' : 'bg-slate-100 text-slate-700'}">
                <strong>${decision.status.replaceAll('_', ' ')}:</strong> ${decision.message}
              </div>
            `).join('') || '<div class="text-slate-500">No stop decisions for this bus.</div>'}
          </div>
        </details>
        <details class="text-xs">
          <summary class="cursor-pointer font-semibold text-brand-600 hover:text-brand-800">
            View Boarded Students List (${bp.boarded_students.length} Allocated)
          </summary>
          <div class="mt-2.5 grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-2">
            ${bp.boarded_students.map(st => `
              <div class="p-2 rounded-lg bg-slate-50 border border-slate-200">
                <span class="font-mono font-bold text-slate-800 text-xs block">${st.student_id}</span>
                <span class="text-[11px] text-slate-600 truncate block">${st.name}</span>
                <span class="text-[10px] text-slate-400 block">${st.stop_id}</span>
              </div>
            `).join('')}
          </div>
        </details>
      </div>
    `;
    container.appendChild(card);
  });
}

// 5. FLEET & BREAKDOWN (Only Mini Bus and Standard Bus)
async function loadFleetCards() {
  try {
    const res = await fetch('/api/buses');
    const buses = await res.json();
    const grid = document.getElementById('fleet-cards-grid');
    grid.innerHTML = '';

    buses.forEach(b => {
      const isBreakdown = b.status === 'breakdown';
      const card = document.createElement('div');
      card.className = `p-5 rounded-2xl border ${isBreakdown ? 'bg-rose-50/70 border-rose-200' : 'bg-white border-slate-200'} shadow-sm space-y-4`;
      
      card.innerHTML = `
        <div class="flex items-center justify-between">
          <span class="text-lg font-black text-slate-900">${b.bus_id}</span>
          <span class="px-2.5 py-0.5 rounded text-xs font-bold ${isBreakdown ? 'bg-rose-200 text-rose-800' : 'bg-emerald-100 text-emerald-800 uppercase'}">
            ${b.status.toUpperCase()}
          </span>
        </div>

        <div class="space-y-1.5 text-xs text-slate-600">
          <div class="flex justify-between">
            <span>Vehicle Type:</span>
            <strong class="text-slate-800">${b.bus_type}</strong>
          </div>
          <div class="flex justify-between">
            <span>Seating Capacity:</span>
            <strong class="text-slate-800">${b.capacity} Seats</strong>
          </div>
          <div class="flex justify-between">
            <span>Currently Boarded:</span>
            <strong class="text-slate-800">${b.boarded_count || 0} Passengers</strong>
          </div>
          <div class="flex justify-between">
            <span>Assigned Driver:</span>
            <strong class="text-slate-800">${b.driver_name}</strong>
          </div>
          <div class="flex justify-between">
            <span>Driver Phone:</span>
            <strong class="text-slate-800 font-mono">${b.driver_phone}</strong>
          </div>
        </div>

        <div class="pt-3 border-t border-slate-200/60">
          ${isBreakdown ? `
            <button onclick="restoreBrokenBus('${b.bus_id}')" class="w-full py-2 bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs rounded-xl shadow transition">
              <i class="fa-solid fa-wrench mr-1"></i>Restore to Active
            </button>
          ` : `
            <button onclick="simulateBreakdownFor('${b.bus_id}')" class="w-full py-2 bg-rose-50 hover:bg-rose-100 text-rose-700 font-bold text-xs rounded-xl border border-rose-200 transition">
              <i class="fa-solid fa-triangle-exclamation mr-1"></i>Simulate Breakdown
            </button>
          `}
        </div>
      `;
      grid.appendChild(card);
    });
  } catch (err) {
    console.error('Error loading fleet:', err);
  }
}

function openBreakdownModal() {
  document.getElementById('modal-breakdown').classList.remove('hidden');
}

function closeBreakdownModal() {
  document.getElementById('modal-breakdown').classList.add('hidden');
}

function simulateBreakdownFor(busId) {
  document.getElementById('breakdown-bus-select').value = busId;
  openBreakdownModal();
}

async function executeBusBreakdown() {
  const busId = document.getElementById('breakdown-bus-select').value;
  try {
    const res = await fetch(`/api/buses/${busId}/breakdown`, { method: 'POST' });
    const data = await res.json();
    closeBreakdownModal();

    if (data.success) {
      const logBox = document.getElementById('breakdown-log-box');
      logBox.classList.remove('hidden');
      logBox.innerHTML = `
        <div class="font-bold flex items-center space-x-1.5 text-rose-800">
          <i class="fa-solid fa-triangle-exclamation"></i>
          <span>Breakdown Emergency Protocol Activated for ${busId}</span>
        </div>
        <p>Intact Boarded Passengers: <strong>${data.boarded_passengers_kept}</strong></p>
        <div class="mt-2 space-y-1">
          <span class="font-semibold block">Available Replacement Buses:</span>
          ${data.available_replacement_buses.length > 0 ? data.available_replacement_buses.map(rep => `
            <div class="p-2 bg-white rounded-lg border border-amber-200 text-slate-800">
              &bull; Bus <strong>${rep.bus_id}</strong> (${rep.bus_type}): <strong>${rep.available_seats} Available Seats</strong>
            </div>
          `).join('') : '<span class="text-rose-600">No other buses have free seats.</span>'}
        </div>
      `;
      loadFleetCards();
      loadAdminDashboard();
    }
  } catch (err) {
    alert('Breakdown protocol error: ' + err.message);
  }
}

async function restoreBrokenBus(busId) {
  try {
    const res = await fetch(`/api/buses/${busId}/restore`, { method: 'POST' });
    const data = await res.json();
    if (data.success) {
      document.getElementById('breakdown-log-box').classList.add('hidden');
      loadFleetCards();
      loadAdminDashboard();
    }
  } catch (err) {
    alert('Restore failed: ' + err.message);
  }
}

// 6. FEE AUDIT REPORTS
async function loadFeeReports() {
  try {
    const res = await fetch('/api/fees/report');
    const records = await res.json();

    let totalCollected = 0;
    let totalPending = 0;
    let paidCount = 0;
    let unpaidCount = 0;

    records.forEach(r => {
      if (r.paid == 1) {
        totalCollected += r.amount;
        paidCount++;
      } else {
        totalPending += r.amount;
        unpaidCount++;
      }
    });

    document.getElementById('fee-report-collected').innerText = `₹${totalCollected.toLocaleString('en-IN')}`;
    document.getElementById('fee-report-paid-count').innerText = `${paidCount} Students Paid`;
    document.getElementById('fee-report-pending').innerText = `₹${totalPending.toLocaleString('en-IN')}`;
    document.getElementById('fee-report-unpaid-count').innerText = `${unpaidCount} Students Pending`;
    document.getElementById('fee-report-total-count').innerText = `${records.length} Registered Students`;
    document.getElementById('fee-report-expected').innerText = `₹${(records.length * 2500).toLocaleString('en-IN')}`;

    const tbody = document.getElementById('fee-records-table-body');
    tbody.innerHTML = '';

    records.forEach(r => {
      const tr = document.createElement('tr');
      tr.className = 'hover:bg-slate-50 transition';

      const statusBadge = r.paid == 1
        ? `<span class="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800">PAID</span>`
        : `<span class="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-rose-100 text-rose-800">PENDING</span>`;

      tr.innerHTML = `
        <td class="py-2.5 px-4 font-mono font-bold text-slate-900">${r.student_id}</td>
        <td class="py-2.5 px-4 font-semibold text-slate-800">${r.name}</td>
        <td class="py-2.5 px-4 text-slate-600">${r.stop_id}</td>
        <td class="py-2.5 px-4 font-mono font-semibold text-slate-800">₹${r.amount.toFixed(2)}</td>
        <td class="py-2.5 px-4 text-center">${statusBadge}</td>
        <td class="py-2.5 px-4">
          <div class="font-mono text-[11px] text-slate-700 font-bold">${r.transaction_id || '--'}</div>
          <div class="text-[10px] text-slate-400">${r.payment_method || 'Unpaid'}</div>
        </td>
        <td class="py-2.5 px-4 text-right">
          <span class="text-[11px] ${r.paid == 1 ? 'text-emerald-700 font-bold' : 'text-slate-400'}">
            ${r.receipt_no || 'Pending Online Payment'}
          </span>
        </td>
      `;
      tbody.appendChild(tr);
    });

  } catch (err) {
    console.error('Error loading fees:', err);
  }
}

// 7. UNALLOCATED STUDENTS
async function loadUnallocated() {
  try {
    const res = await fetch('/api/students/unallocated');
    const list = await res.json();
    const container = document.getElementById('unallocated-list-container');
    const badge = document.getElementById('unallocated-badge-count');
    badge.innerText = `${list.length} Unallocated`;

    if (list.length === 0) {
      container.innerHTML = `
        <div class="text-center py-8 text-slate-400">
          <i class="fa-solid fa-circle-check text-emerald-500 text-3xl mb-2"></i>
          <p class="text-sm">All eligible students have been successfully allocated seats across the 3 buses!</p>
        </div>
      `;
      return;
    }

    container.innerHTML = `
      <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
        ${list.map(s => `
          <div class="p-3.5 rounded-xl border border-rose-200 bg-rose-50 text-xs space-y-1">
            <div class="flex items-center justify-between">
              <span class="font-mono font-bold text-slate-900">${s.student_id}</span>
              <span class="px-2 py-0.5 rounded text-[10px] font-bold bg-rose-200 text-rose-800">Waiting for Seat</span>
            </div>
            <div class="font-semibold text-slate-800">${s.name}</div>
            <div class="text-[11px] text-slate-600">${s.department}</div>
            <div class="text-[11px] text-slate-500">Assigned Stop: <strong>${s.stop_id}</strong></div>
          </div>
        `).join('')}
      </div>
    `;
  } catch (err) {
    console.error('Error loading unallocated:', err);
  }
}
