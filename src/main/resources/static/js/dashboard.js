/* ── Allocation bar colors ──────────────────────────────── */
const SEGMENT_COLORS = [
  '#6366f1', '#22c55e', '#f59e0b', '#3b82f6', '#ec4899',
  '#14b8a6', '#f97316', '#a855f7', '#84cc16', '#06b6d4'
];

document.querySelectorAll('.alloc-segment').forEach((el, i) => {
  el.style.background = SEGMENT_COLORS[i % SEGMENT_COLORS.length];
});

/* ── Edit modal ─────────────────────────────────────────── */
function openEdit(id, ticker, shares, target) {
  document.getElementById('editTicker').textContent = ticker;
  document.getElementById('editShares').value = shares;
  document.getElementById('editTarget').value = target;
  document.getElementById('editForm').action = '/holdings/' + id + '/update';
  document.getElementById('editModal').classList.add('open');
}

// Attach click handler to all edit buttons
document.addEventListener('DOMContentLoaded', function() {
  document.querySelectorAll('.edit-btn').forEach(btn => {
    btn.addEventListener('click', function() {
      const id = this.getAttribute('data-id');
      const ticker = this.getAttribute('data-ticker');
      const shares = this.getAttribute('data-shares');
      const target = this.getAttribute('data-target');
      openEdit(id, ticker, shares, target);
    });
  });
});

function closeEdit(event) {
  if (event.target === document.getElementById('editModal')) {
    document.getElementById('editModal').classList.remove('open');
  }
}

document.addEventListener('keydown', function(e) {
  if (e.key === 'Escape') {
    document.getElementById('editModal').classList.remove('open');
  }
});

/* ── Auto-refresh every 5 minutes during market hours ───── */
function isMarketHours() {
  const now = new Date();
  const et = new Date(now.toLocaleString('en-US', { timeZone: 'America/New_York' }));
  const day = et.getDay(); // 0=Sun, 6=Sat
  if (day === 0 || day === 6) return false;
  const h = et.getHours(), m = et.getMinutes();
  const minutes = h * 60 + m;
  return minutes >= 9 * 60 + 30 && minutes <= 16 * 60;
}

if (isMarketHours()) {
  setTimeout(() => window.location.reload(), 5 * 60 * 1000);
}
