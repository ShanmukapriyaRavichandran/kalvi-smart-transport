// Kalvi College Transport Management System - Main App State & Navigation

let currentUser = null;

document.addEventListener('DOMContentLoaded', () => {
  initClock();
  checkCurrentSession();
});

// Live clock
function initClock() {
  const clockEl = document.getElementById('live-time');
  if (!clockEl) return;
  const update = () => {
    const now = new Date();
    clockEl.innerText = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  };
  update();
  setInterval(update, 1000);
}

// Check current user session
async function checkCurrentSession() {
  try {
    const res = await fetch('/api/auth/me');
    const data = await res.json();
    if (data && data.user) {
      currentUser = data.user;
    } else {
      currentUser = null;
    }
  } catch (err) {
    currentUser = null;
  }
  applyUserView();
}

// Apply UI view based on currentUser role
function applyUserView() {
  const adminNav = document.getElementById('admin-navbar');
  const viewLogin = document.getElementById('view-login');
  const viewAdmin = document.getElementById('view-admin');
  const viewStudent = document.getElementById('view-student');
  const roleLabel = document.getElementById('current-role-label');
  const authBtnLabel = document.getElementById('auth-btn-label');
  const authBtnIcon = document.getElementById('auth-btn-icon');

  if (!currentUser) {
    // NOT LOGGED IN -> Show Login View
    adminNav.classList.add('hidden');
    viewAdmin.classList.add('hidden');
    viewStudent.classList.add('hidden');
    viewLogin.classList.remove('hidden');

    roleLabel.innerText = 'Not Signed In';
    roleLabel.className = 'text-xs font-semibold uppercase tracking-wider text-slate-400';
    authBtnLabel.innerText = 'Sign In';
    authBtnIcon.className = 'fa-solid fa-arrow-right-to-bracket';
    return;
  }

  // LOGGED IN
  viewLogin.classList.add('hidden');
  authBtnLabel.innerText = 'Logout';
  authBtnIcon.className = 'fa-solid fa-arrow-right-from-bracket';

  if (currentUser.role === 'admin') {
    adminNav.classList.remove('hidden');
    viewAdmin.classList.remove('hidden');
    viewStudent.classList.add('hidden');
    roleLabel.innerText = 'Administrator (kalvi@transport)';
    roleLabel.className = 'text-xs font-semibold uppercase tracking-wider text-amber-400';
    loadAdminDashboard();
  } else if (currentUser.role === 'student') {
    adminNav.classList.add('hidden');
    viewAdmin.classList.add('hidden');
    viewStudent.classList.remove('hidden');
    roleLabel.innerText = `Student: ${currentUser.student_id || currentUser.id}`;
    roleLabel.className = 'text-xs font-semibold uppercase tracking-wider text-emerald-400';
    loadStudentPortal(currentUser.student_id || currentUser.id);
  }
}

// Header Auth Action (Logout if logged in, or show login if logged out)
async function handleAuthAction() {
  if (currentUser) {
    try {
      await fetch('/api/auth/logout', { method: 'POST' });
    } catch (e) {}
    currentUser = null;
    applyUserView();
  } else {
    // Already in login view
    document.getElementById('view-login').classList.remove('hidden');
  }
}

// Login Portal selector between Student and Admin
function selectLoginPortal(portal) {
  const studentTab = document.getElementById('login-tab-student');
  const adminTab = document.getElementById('login-tab-admin');
  const studentForm = document.getElementById('login-form-student');
  const adminForm = document.getElementById('login-form-admin');

  if (portal === 'student') {
    studentTab.className = 'py-2.5 rounded-xl bg-white shadow-sm text-slate-900 transition flex items-center justify-center space-x-1.5 font-bold';
    adminTab.className = 'py-2.5 rounded-xl text-slate-600 transition flex items-center justify-center space-x-1.5 font-bold';
    studentForm.classList.remove('hidden');
    adminForm.classList.add('hidden');
  } else {
    adminTab.className = 'py-2.5 rounded-xl bg-white shadow-sm text-slate-900 transition flex items-center justify-center space-x-1.5 font-bold';
    studentTab.className = 'py-2.5 rounded-xl text-slate-600 transition flex items-center justify-center space-x-1.5 font-bold';
    adminForm.classList.remove('hidden');
    studentForm.classList.add('hidden');
  }
}

// Unified Student Login (User ID is Roll No, Password is RollNo@BusNo)
async function submitStudentLogin(e) {
  e.preventDefault();
  const student_id = document.getElementById('student-input-id').value.trim().toUpperCase();
  const password = document.getElementById('student-input-pwd').value.trim();

  if (!student_id) {
    alert('Please enter your Student Roll Number (e.g. 25CSR265, 25CSR201)');
    return;
  }

  try {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ role: 'student', student_id, password })
    });
    const data = await res.json();
    if (data.success) {
      currentUser = data.user;
      applyUserView();
    } else {
      alert(data.message || 'Login failed. Please verify your Student Roll Number and Password.');
    }
  } catch (err) {
    alert('Connection error: ' + err.message);
  }
}

// Admin Login (User ID: kalvi@transport | Password: kalvi123)
async function submitAdminLogin(e) {
  e.preventDefault();
  const username = document.getElementById('admin-input-user').value.trim();
  const password = document.getElementById('admin-input-pwd').value.trim();

  try {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ role: 'admin', username, password })
    });
    const data = await res.json();
    if (data.success) {
      currentUser = data.user;
      applyUserView();
    } else {
      alert(data.message || 'Invalid administrator credentials (kalvi@transport / kalvi123)');
    }
  } catch (err) {
    alert('Connection error: ' + err.message);
  }
}

// ----------------- FORGOT PASSWORD MODAL ----------------- //
function openForgotPasswordModal(defaultRole = 'student') {
  document.getElementById('modal-forgot-password').classList.remove('hidden');
  switchForgotTab(defaultRole);
  const resultBox = document.getElementById('forgot-result-box');
  if (resultBox) {
    resultBox.classList.add('hidden');
    resultBox.innerHTML = '';
  }
}

function closeForgotPasswordModal() {
  document.getElementById('modal-forgot-password').classList.add('hidden');
}

function switchForgotTab(role) {
  const tabStudent = document.getElementById('forgot-tab-student');
  const tabAdmin = document.getElementById('forgot-tab-admin');
  const formStudent = document.getElementById('forgot-form-student');
  const formAdmin = document.getElementById('forgot-form-admin');
  const resultBox = document.getElementById('forgot-result-box');
  if (resultBox) resultBox.classList.add('hidden');

  if (role === 'student') {
    tabStudent.className = 'py-2 rounded-lg bg-white shadow-sm text-slate-900 font-bold';
    tabAdmin.className = 'py-2 rounded-lg text-slate-600 font-bold';
    formStudent.classList.remove('hidden');
    formAdmin.classList.add('hidden');
  } else {
    tabAdmin.className = 'py-2 rounded-lg bg-white shadow-sm text-slate-900 font-bold';
    tabStudent.className = 'py-2 rounded-lg text-slate-600 font-bold';
    formAdmin.classList.remove('hidden');
    formStudent.classList.add('hidden');
  }
}

async function handleForgotStudent(e) {
  e.preventDefault();
  const roll_no = document.getElementById('forgot-roll-input').value.trim().toUpperCase();
  const resultBox = document.getElementById('forgot-result-box');

  try {
    const res = await fetch('/api/auth/forgot-password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ role: 'student', roll_no })
    });
    const data = await res.json();

    resultBox.classList.remove('hidden');
    if (data.success) {
      resultBox.className = 'p-3.5 rounded-xl bg-emerald-50 border border-emerald-200 text-xs text-emerald-950 space-y-1 block';
      resultBox.innerHTML = `
        <div class="font-bold text-emerald-800 flex items-center space-x-1">
          <i class="fa-solid fa-circle-check"></i>
          <span>Password Retrieved for ${data.name}</span>
        </div>
        <div>Roll Number: <strong class="font-mono text-slate-900">${data.roll_no}</strong></div>
        <div>Assigned Bus: <strong>${data.bus_id}</strong></div>
        <div>Password: <strong class="font-mono text-emerald-700 font-bold text-sm bg-white px-2 py-0.5 rounded border border-emerald-200">${data.password}</strong></div>
        <p class="text-[10px] text-emerald-700 mt-1">Formula is (Roll No @ Bus No): ${data.roll_no}@${data.bus_id}</p>
      `;
    } else {
      resultBox.className = 'p-3 rounded-xl bg-rose-50 border border-rose-200 text-xs text-rose-800 block';
      resultBox.innerText = data.message || 'Roll Number not found in registry.';
    }
  } catch (err) {
    alert('Error: ' + err.message);
  }
}

async function handleForgotAdmin(e) {
  e.preventDefault();
  const verify_code = document.getElementById('forgot-admin-code').value.trim();
  const resultBox = document.getElementById('forgot-result-box');

  try {
    const res = await fetch('/api/auth/forgot-password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ role: 'admin', verify_code })
    });
    const data = await res.json();

    resultBox.classList.remove('hidden');
    if (data.success) {
      resultBox.className = 'p-3.5 rounded-xl bg-emerald-50 border border-emerald-200 text-xs text-emerald-950 space-y-1 block';
      resultBox.innerHTML = `
        <div class="font-bold text-emerald-800 flex items-center space-x-1">
          <i class="fa-solid fa-shield-halved"></i>
          <span>Administrator Credentials Recovered</span>
        </div>
        <div>Admin User ID: <strong class="font-mono text-slate-900 font-bold">${data.username}</strong></div>
        <div>Admin Password: <strong class="font-mono text-emerald-700 font-bold text-sm bg-white px-2 py-0.5 rounded border border-emerald-200">${data.password}</strong></div>
      `;
    } else {
      resultBox.className = 'p-3 rounded-xl bg-rose-50 border border-rose-200 text-xs text-rose-800 block';
      resultBox.innerText = data.message || 'Verification keyword incorrect.';
    }
  } catch (err) {
    alert('Error: ' + err.message);
  }
}
