/* ─────────────────────────────────────────────────────────────────
   booking.js — Route selection + Seat map interaction
   ───────────────────────────────────────────────────────────────── */

let selectedRoute   = null;
let selectedSeats   = [];
let currentUserId   = null;
let myExistingSeats = new Set();

// ── Initialisation ─────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
  try {
    const res  = await fetch('/bus/api/auth/me', { credentials: 'same-origin' });
    const user = await res.json();
    if (!user.id) { location.href = 'login.html'; return; }

    currentUserId = user.id;
    document.getElementById('userGreeting').textContent =
      `// Welcome, ${user.name} · Roll: ${user.rollNo}`;
  } catch {
    location.href = 'login.html';
    return;
  }

  const today = new Date().toISOString().split('T')[0];
  document.getElementById('travelDate').value = today;
  document.getElementById('travelDate').min   = today;

  loadRoutes();
});

// ── Load routes ────────────────────────────────────────────────────
async function loadRoutes() {
  const date = document.getElementById('travelDate').value;
  if (!date) { showAlert('bookingAlert', 'error', 'Please select a travel date.'); return; }

  selectedRoute = null;
  selectedSeats = [];
  document.getElementById('seatMapSection').style.display = 'none';
  document.getElementById('routesContainer').innerHTML = '<div class="spinner"></div>';

  try {
    const res  = await fetch(`/bus/api/routes?date=${date}`, { credentials: 'same-origin' });
    const data = await res.json();

    if (!res.ok) { showAlert('bookingAlert', 'error', data.error || 'Failed to load routes.'); return; }

    if (!data.length) {
      document.getElementById('routesContainer').innerHTML = `
        <div class="empty-state">
          <div class="icon">🚌</div>
          <h3>NO ROUTES FOUND</h3>
          <p>No bus routes are scheduled for this date.</p>
        </div>`;
      return;
    }

    renderRoutes(data, date);
  } catch {
    showAlert('bookingAlert', 'error', 'Network error. Please try again.');
    document.getElementById('routesContainer').innerHTML = '';
  }
}

// ── Render route cards ─────────────────────────────────────────────
function renderRoutes(routes, date) {
  const html = `<div class="routes-grid">` +
    routes.map(r => {
      const pct = r.bookedSeats / r.totalSeats;
      const seatClass = pct >= 1 ? 'seats-full' : pct >= 0.8 ? 'seats-low' : 'seats-available';
      const seatLabel = pct >= 1 ? 'FULL' : `${r.availableSeats} left`;

      return `
        <div class="route-card" onclick="selectRoute(${r.id}, '${date}', this)" data-route-id="${r.id}">
          <div class="route-name">${escHtml(r.routeName)}</div>
          <div class="route-path">
            ${escHtml(r.origin)}
            <span class="arrow"> ──▶ </span>
            ${escHtml(r.destination)}
          </div>
          <div class="route-meta">
            <div class="route-meta-item">
              <label>Departure</label>
              <span>${formatTime(r.departure)}</span>
            </div>
            <div class="route-meta-item">
              <label>Available</label>
              <span class="${seatClass}">${seatLabel}</span>
            </div>
            <div class="route-meta-item">
              <label>Fare</label>
              <span>₹${Number(r.fare).toFixed(2)}</span>
            </div>
            <div class="route-meta-item">
              <label>Capacity</label>
              <span>${r.totalSeats} seats</span>
            </div>
          </div>
        </div>`;
    }).join('') +
  `</div>`;

  document.getElementById('routesContainer').innerHTML = html;
}

// ── Select a route → load seat map ────────────────────────────────
async function selectRoute(routeId, date, cardEl) {
  document.querySelectorAll('.route-card').forEach(c => c.classList.remove('selected'));
  cardEl.classList.add('selected');

  selectedRoute = routeId;
  selectedSeats = [];

  const section = document.getElementById('seatMapSection');
  section.style.display = 'block';
  document.getElementById('seatGrid').innerHTML = '<div class="spinner"></div>';
  section.scrollIntoView({ behavior: 'smooth', block: 'start' });

  try {
    const res  = await fetch(`/bus/api/bookings?routeId=${routeId}&date=${date}`, { credentials: 'same-origin' });
    const data = await res.json();

    if (!res.ok) { showAlert('bookingAlert', 'error', data.error || 'Failed to load seat map.'); return; }

    myExistingSeats = new Set(
      data.bookedSeats.filter(s => s.bookedBy === sessionUserName).map(s => s.seatNo)
    );

    renderSeatMap(data.totalSeats, data.bookedSeats);
    updateSeatMapTitle(routeId);
  } catch {
    showAlert('bookingAlert', 'error', 'Network error loading seat map.');
  }
}

// Store current username for "my seat" detection
let sessionUserName = '';
fetch('/bus/api/auth/me', { credentials: 'same-origin' })
  .then(r => r.json())
  .then(u => { sessionUserName = u.name || ''; });

// ── Render seat map ────────────────────────────────────────────────
function renderSeatMap(totalSeats, bookedSeats) {
  const bookedMap = {};
  bookedSeats.forEach(b => { bookedMap[b.seatNo] = b.bookedBy; });

  const grid = document.getElementById('seatGrid');
  let html   = '';

  for (let i = 1; i <= totalSeats; i++) {
    const col = ((i - 1) % 8) + 1;

    if (col === 5) html += `<div class="seat-aisle"></div>`;

    let cls   = 'seat-available';
    let title = `Seat ${i} — Available`;

    if (bookedMap[i]) {
      if (bookedMap[i] === sessionUserName) {
        cls   = 'seat-mine';
        title = `Seat ${i} — Your booking`;
      } else {
        cls   = 'seat-booked';
        title = `Seat ${i} — Booked`;
      }
    }

    const clickable = cls === 'seat-available' ? `onclick="pickSeat(${i}, this)"` : '';
    html += `<div class="seat ${cls}" title="${title}" ${clickable}>${i}</div>`;
  }

  grid.innerHTML = html;
  updateConfirmRow();
}

// ── Pick / deselect a seat ─────────────────────────────────────────
function pickSeat(seatNo, el) {
  if (selectedSeats.includes(seatNo)) {
    selectedSeats = selectedSeats.filter(s => s !== seatNo);
    el.classList.remove('seat-selected');
    el.classList.add('seat-available');
  } else {
    selectedSeats.push(seatNo);
    el.classList.remove('seat-available');
    el.classList.add('seat-selected');
  }
  updateConfirmRow();
}

// ── Confirm booking ────────────────────────────────────────────────
async function confirmBooking() {
  if (!selectedSeats.length || !selectedRoute) return;

  const date = document.getElementById('travelDate').value;
  const btn  = document.getElementById('confirmBtn');
  btn.disabled = true;
  btn.textContent = 'Confirming…';

  let successCount = 0;
  let lastBookingId = null;
  let errors = [];

  for (const seatNo of selectedSeats) {
    try {
      const res = await fetch('/bus/api/bookings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ routeId: selectedRoute, seatNo, date })
      });
      const data = await res.json();

      if (res.ok) {
        successCount++;
        lastBookingId = data.bookingId;
      } else {
        errors.push(`Seat ${seatNo}: ${data.error}`);
      }
    } catch {
      errors.push(`Seat ${seatNo}: Network error`);
    }
  }

  if (successCount > 0) {
    showAlert('bookingAlert', 'success',
      `✓ ${successCount} seat(s) confirmed! Last Booking ID: #${lastBookingId}`);
  }
  if (errors.length > 0) {
    showAlert('bookingAlert', 'error', errors.join(' | '));
  }

  selectedSeats = [];
  const card = document.querySelector(`.route-card[data-route-id="${selectedRoute}"]`);
  if (card) selectRoute(selectedRoute, date, card);
  loadRoutes();

  btn.disabled = false;
  btn.textContent = 'Confirm Booking';
}

// ── Logout ─────────────────────────────────────────────────────────
async function logout() {
  await fetch('/bus/api/auth/logout', { method: 'POST', credentials: 'same-origin' });
  location.href = 'login.html';
}

// ── UI helpers ─────────────────────────────────────────────────────
function updateConfirmRow() {
  const info = document.getElementById('selectedInfo');
  const btn  = document.getElementById('confirmBtn');

  if (selectedSeats.length > 0) {
    info.innerHTML = `Selected seats: <strong>${selectedSeats.join(', ')}</strong>`;
    btn.disabled   = false;
  } else {
    info.innerHTML = 'No seat selected';
    btn.disabled   = true;
  }
}

function updateSeatMapTitle(routeId) {
  const card = document.querySelector(`.route-card[data-route-id="${routeId}"]`);
  if (!card) return;
  const name = card.querySelector('.route-name')?.textContent || '';
  document.getElementById('seatMapTitle').textContent =
    `SELECT YOUR SEAT — ${name.toUpperCase()}`;
}

function showAlert(id, type, msg) {
  const el = document.getElementById(id);
  el.className = `alert alert-${type} show`;
  el.textContent = msg;
  el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  if (type === 'success') setTimeout(() => el.classList.remove('show'), 5000);
}

function formatTime(t) {
  if (!t) return '—';
  const [h, m] = t.split(':');
  const hour = parseInt(h);
  return `${hour % 12 || 12}:${m} ${hour < 12 ? 'AM' : 'PM'}`;
}

function escHtml(str) {
  const d = document.createElement('div');
  d.textContent = str;
  return d.innerHTML;
} 