// Kalvi College Transport Management System - Student Portal Logic

let activeStudentData = null;
let currentPaymentMethod = 'UPI (Google Pay / PhonePe)';

// Load Student Portal data
async function loadStudentPortal(studentId) {
  try {
    const res = await fetch(`/api/student/${studentId}/portal`);
    const data = await res.json();
    if (!data.success) {
      alert(data.message || 'Student not found');
      return;
    }

    activeStudentData = data;
    const st = data.student;
    const fee = data.fee || {};

    // 1. Banner Info
    document.getElementById('portal-student-id').innerText = st.student_id;
    document.getElementById('portal-student-name').innerText = st.name;
    document.getElementById('portal-student-dept').innerText = `${st.department || 'B.E. Computer Science'} • ${st.semester || '6th Semester'}`;

    // 2. Student Self-Attendance Banner Controls
    const attText = document.getElementById('banner-att-text');
    const attStatusBox = document.getElementById('banner-attendance-status');
    const attBtnLabel = document.getElementById('btn-self-att-label');

    if (st.present == 1) {
      attStatusBox.className = 'text-sm font-bold text-emerald-400 flex items-center space-x-1';
      attStatusBox.innerHTML = `<i class="fa-solid fa-circle-check"></i><span>PRESENT</span>`;
      attBtnLabel.innerText = 'Mark as Absent';
    } else {
      attStatusBox.className = 'text-sm font-bold text-slate-400 flex items-center space-x-1';
      attStatusBox.innerHTML = `<i class="fa-solid fa-circle-xmark"></i><span>ABSENT</span>`;
      attBtnLabel.innerText = 'Mark as Present';
    }

    // 3. Digital ID Pass Card
    document.getElementById('pass-student-name').innerText = st.name;
    document.getElementById('pass-student-id').innerText = st.student_id;
    document.getElementById('pass-stop-id').innerText = st.stop_name || st.stop_id;

    const authBadge = document.getElementById('pass-authorized-badge');
    if (st.authorized == 1) {
      authBadge.className = 'font-bold text-emerald-400 flex items-center space-x-1';
      authBadge.innerHTML = `<i class="fa-solid fa-circle-check"></i><span>AUTHORIZED</span>`;
    } else {
      authBadge.className = 'font-bold text-amber-400 flex items-center space-x-1';
      authBadge.innerHTML = `<i class="fa-solid fa-circle-exclamation"></i><span>PENDING FEE</span>`;
    }

    const presBadge = document.getElementById('pass-present-badge');
    if (st.present == 1) {
      presBadge.className = 'font-bold text-emerald-400';
      presBadge.innerText = 'PRESENT';
    } else {
      presBadge.className = 'font-bold text-slate-400';
      presBadge.innerText = 'ABSENT';
    }

    const busBadge = document.getElementById('pass-assigned-bus');
    if (st.boarded == 1 && st.assigned_bus_id) {
      const busTypeName = st.assigned_bus_id === 'B01' ? 'Mini Bus' : 'Standard Bus';
      busBadge.innerText = `${st.assigned_bus_id} (${busTypeName})`;
    } else {
      busBadge.innerText = 'Waiting / Unassigned';
    }

    // 4. Stop & Route Info
    document.getElementById('portal-stop-name').innerText = st.stop_name || st.stop_id;
    document.getElementById('portal-stop-landmark').innerText = st.landmark || 'Assigned Pickup Junction';
    document.getElementById('portal-stop-distance').innerText = `${data.travel_time_from_campus || 10} Mins`;
    document.getElementById('portal-driver-name').innerText = st.driver_name || 'R. Murugesan (Driver)';
    document.getElementById('portal-driver-phone').innerText = st.driver_phone || '+91 98421 11201';

    // 5. Fee Card & Action Area
    const statusContainer = document.getElementById('portal-fee-status-badge');
    const noticeContainer = document.getElementById('portal-fee-notice');
    const actionArea = document.getElementById('portal-fee-action-area');
    const methodEl = document.getElementById('portal-fee-method');
    const txnidEl = document.getElementById('portal-fee-txnid');
    const eligibilityEl = document.getElementById('portal-fee-eligibility');
    const paymentOptionsBox = document.getElementById('portal-payment-options-box');

    const isPaid = fee.paid == 1;

    if (isPaid) {
      statusContainer.innerHTML = `
        <span class="px-3 py-1 rounded-full text-xs font-bold bg-emerald-100 text-emerald-800 flex items-center space-x-1">
          <i class="fa-solid fa-circle-check"></i>
          <span>PAID & AUTHORIZED</span>
        </span>
      `;
      eligibilityEl.innerText = 'Authorized for Bus';
      methodEl.innerText = fee.payment_method || 'Online Payment';
      txnidEl.innerText = fee.transaction_id || 'TXN-KLV-VERIFIED';

      if (paymentOptionsBox) paymentOptionsBox.classList.add('hidden');

      noticeContainer.className = 'mt-4 p-4 rounded-2xl text-xs leading-relaxed bg-emerald-50 border border-emerald-200 text-emerald-900 flex items-start space-x-3';
      noticeContainer.innerHTML = `
        <i class="fa-solid fa-shield-check text-emerald-600 text-base mt-0.5"></i>
        <div>
          <strong class="font-bold">Transport Clearance Active:</strong> Your transport fee of ₹2,500.00 is settled in full. Your digital college transport pass is active and authorized for daily bus boarding across all scheduled college routes.
        </div>
      `;

      actionArea.innerHTML = `
        <button onclick="openReceiptModal()" class="w-full sm:w-auto px-6 py-2.5 bg-slate-900 hover:bg-slate-800 text-white font-bold rounded-xl text-xs shadow-md transition flex items-center justify-center space-x-2">
          <i class="fa-solid fa-receipt text-amber-400"></i>
          <span>Download / Print Official Fee Receipt</span>
        </button>
      `;
    } else {
      statusContainer.innerHTML = `
        <span class="px-3 py-1 rounded-full text-xs font-bold bg-rose-100 text-rose-800 flex items-center space-x-1">
          <i class="fa-solid fa-clock"></i>
          <span>PAYMENT DUE</span>
        </span>
      `;
      eligibilityEl.innerText = 'Pending Payment';
      methodEl.innerText = 'Awaiting Payment';
      txnidEl.innerText = 'Action Required';

      if (paymentOptionsBox) paymentOptionsBox.classList.remove('hidden');

      noticeContainer.className = 'mt-4 p-4 rounded-2xl text-xs leading-relaxed bg-amber-50 border border-amber-200 text-amber-900 flex items-start space-x-3';
      noticeContainer.innerHTML = `
        <i class="fa-solid fa-triangle-exclamation text-amber-600 text-base mt-0.5"></i>
        <div>
          <strong class="font-bold">Pass Requires Payment:</strong> Your semester transport fee of ₹2,500.00 is pending. Select an option above and pay online to immediately activate your pass authorization for daily pickup.
        </div>
      `;

      actionArea.innerHTML = `
        <button onclick="openPaymentModal()" class="w-full sm:w-auto px-8 py-3 bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-700 hover:to-teal-700 text-white font-bold rounded-xl text-sm shadow-lg shadow-emerald-600/20 transition flex items-center justify-center space-x-2 animate-pulse">
          <i class="fa-solid fa-credit-card"></i>
          <span>Proceed to Pay Transport Fee (₹2,500.00)</span>
        </button>
      `;
    }

  } catch (err) {
    console.error('Error loading student portal:', err);
  }
}

// Student Self-Attendance Toggle
async function toggleStudentSelfAttendance() {
  if (!activeStudentData) return;
  const studentId = activeStudentData.student.student_id;
  const currentPresent = activeStudentData.student.present == 1;
  const newPresent = currentPresent ? 0 : 1;

  try {
    const res = await fetch(`/api/students/${studentId}/attendance`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ present: newPresent })
    });
    const data = await res.json();
    if (data.success) {
      // Reload student portal to update UI
      loadStudentPortal(studentId);
    } else {
      alert('Could not update attendance: ' + (data.message || 'Error'));
    }
  } catch (err) {
    alert('Attendance update failed: ' + err.message);
  }
}

// Payment modal controls
function openPaymentModal() {
  if (!activeStudentData) return;
  const modal = document.getElementById('modal-payment');
  document.getElementById('checkout-order-id').innerText = `KLV-2026-${activeStudentData.student.student_id}`;
  document.getElementById('card-holder-name').value = activeStudentData.student.name;
  
  document.getElementById('payment-processing-box').classList.add('hidden');
  document.getElementById('btn-submit-payment').disabled = false;
  
  modal.classList.remove('hidden');
}

function closePaymentModal() {
  document.getElementById('modal-payment').classList.add('hidden');
}

function setPaymentMethod(method) {
  const tabs = ['upi', 'card', 'netbanking'];
  tabs.forEach(m => {
    const tabBtn = document.getElementById(`paytab-${m}`);
    const tabContent = document.getElementById(`paycontent-${m}`);
    if (m === method) {
      tabBtn.className = 'py-2 rounded-lg bg-white shadow-sm text-slate-900 font-bold flex items-center justify-center space-x-1.5';
      tabContent.classList.remove('hidden');
    } else {
      tabBtn.className = 'py-2 rounded-lg text-slate-600 font-bold flex items-center justify-center space-x-1.5';
      tabContent.classList.add('hidden');
    }
  });

  if (method === 'upi') currentPaymentMethod = 'UPI (Google Pay / PhonePe)';
  else if (method === 'card') currentPaymentMethod = 'Debit/Credit Card';
  else currentPaymentMethod = 'Net Banking (SBI)';
}

// Execute online payment simulation
async function executeFeePayment() {
  if (!activeStudentData) return;
  const studentId = activeStudentData.student.student_id;
  const processBox = document.getElementById('payment-processing-box');
  const submitBtn = document.getElementById('btn-submit-payment');

  processBox.classList.remove('hidden');
  submitBtn.disabled = true;

  try {
    await new Promise(r => setTimeout(r, 1200));

    const res = await fetch(`/api/student/${studentId}/pay-fee`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ payment_method: currentPaymentMethod })
    });
    const data = await res.json();

    if (data.success) {
      closePaymentModal();
      await loadStudentPortal(studentId);
      openReceiptModal(data.receipt);
    } else {
      alert(data.message || 'Payment processing failed');
    }
  } catch (err) {
    alert('Payment error: ' + err.message);
  } finally {
    processBox.classList.add('hidden');
    submitBtn.disabled = false;
  }
}

// Receipt Modal controls
function openReceiptModal(customReceipt) {
  if (!activeStudentData) return;
  const st = activeStudentData.student;
  const fee = activeStudentData.fee || {};
  const rcpt = customReceipt || fee;

  document.getElementById('rcpt-no').innerText = rcpt.receipt_no || fee.receipt_no || 'RCPT-2026-KLV';
  document.getElementById('rcpt-date').innerText = rcpt.payment_date || fee.payment_date || new Date().toLocaleString();
  document.getElementById('rcpt-txnid').innerText = rcpt.transaction_id || fee.transaction_id || 'TXN-KLV-APPROVED';
  document.getElementById('rcpt-method').innerText = rcpt.payment_method || fee.payment_method || 'Online Payment';

  document.getElementById('rcpt-studentid').innerText = st.student_id;
  document.getElementById('rcpt-studentname').innerText = st.name;
  document.getElementById('rcpt-dept').innerText = st.department || 'B.E. Computer Science';
  document.getElementById('rcpt-stop').innerText = st.stop_name || st.stop_id;

  document.getElementById('modal-receipt').classList.remove('hidden');
}

function closeReceiptModal() {
  document.getElementById('modal-receipt').classList.add('hidden');
}
