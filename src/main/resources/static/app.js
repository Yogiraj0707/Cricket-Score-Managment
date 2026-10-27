const API = '/api';
let matches = [];
let selectedMatchId = 'ipl-final';
let eventSource;
let activeTab = 'batting';
let statsTab = 'batting';

const $ = (selector) => document.querySelector(selector);
const THEME_KEY = 'boundary.theme';
let authMode = 'register';
let editingProfile = false;
const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
}[char]));
const scoreText = (team) => team && team.ballsFaced ? `${team.runs}/${team.wickets}` : 'Yet to bat';
const oversText = (team) => `${Math.floor(team.ballsFaced / 6)}.${team.ballsFaced % 6}`;

async function loadMatches() {
  try {
    const response = await fetch(`${API}/matches`);
    if (!response.ok) throw new Error(`Could not load matches (${response.status})`);
    matches = await response.json();
    renderMatches();
    renderFeatured();
    renderPerformers();
    renderPlayerStats();
    renderMatchSummaries();
  } catch (error) {
    $('#all-matches').innerHTML = `<div class="loading-card">${escapeHtml(error.message)}. Refresh to try again.</div>`;
  }
}

function renderMatches() {
  $('#match-count').textContent = String(matches.length).padStart(2, '0');
  $('#heading-count').textContent = matches.length;
  const sorted = [...matches].sort((a, b) => statusOrder(a.status) - statusOrder(b.status));
  $('#all-matches').innerHTML = sorted.map((match) => {
    const statusClass = match.status.toLowerCase();
    const status = match.status === 'LIVE' ? 'LIVE' : match.status === 'UPCOMING' ? 'UPCOMING' : 'COMPLETED';
    const selected = match.id === selectedMatchId ? ' selected-card' : '';
    const result = match.status === 'UPCOMING' ? match.result : match.status === 'COMPLETED' ? match.result : `${match.battingTeam} batting`;
    return `<article class="match-card${selected}" data-match-id="${escapeHtml(match.id)}" tabindex="0" role="button" aria-label="View ${escapeHtml(match.tournament)} match">
      <div class="match-card-top"><span>${escapeHtml(match.tournament.toUpperCase())}</span><span class="status-label ${statusClass}">${status}</span></div>
      <div class="match-teams">${teamRow(match.teamA)}${teamRow(match.teamB)}</div>
      <div class="match-card-bottom"><span>${escapeHtml(match.venue.split(',')[0])}</span><strong>${escapeHtml(result || '')}</strong></div>
    </article>`;
  }).join('');
  document.querySelectorAll('.match-card').forEach((card) => {
    const choose = () => selectMatch(card.dataset.matchId);
    card.addEventListener('click', choose);
    card.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); choose(); }
    });
  });
}

function statusOrder(status) {
  return status === 'LIVE' ? 0 : status === 'UPCOMING' ? 1 : 2;
}

function teamRow(team) {
  const initial = escapeHtml(team.shortName.slice(0, 3));
  const played = team.ballsFaced > 0;
  return `<div class="mini-team"><span class="mini-crest">${initial}</span><span class="mini-team-name">${escapeHtml(team.name)}</span><span class="mini-score">${played ? `${team.runs}/${team.wickets} <span class="mini-overs">(${oversText(team)})</span>` : '—'}</span></div>`;
}

function renderFeatured() {
  const match = matches.find((item) => item.id === selectedMatchId) || matches.find((item) => item.status === 'LIVE');
  if (!match) return;
  selectedMatchId = match.id;
  const batting = match.battingTeam === match.teamA.name ? match.teamA : match.teamB;
  const bowling = match.battingTeam === match.teamA.name ? match.teamB : match.teamA;
  const ballsRemaining = Math.max(0, 120 - batting.ballsFaced);
  const runsNeeded = Math.max(0, (match.target || 0) - batting.runs);
  const crr = batting.ballsFaced ? (batting.runs * 6 / batting.ballsFaced).toFixed(2) : '0.00';
  const rrr = ballsRemaining ? (runsNeeded * 6 / ballsRemaining).toFixed(2) : '—';
  const hero = $('#featured-match');
  const live = match.status === 'LIVE';
  hero.querySelector('.live-pill').innerHTML = `<span></span> ${live ? 'LIVE NOW' : match.status}`;
  hero.querySelector('.hero-league').innerHTML = `${escapeHtml(match.tournament.toUpperCase())} <b>·</b> ${escapeHtml(match.matchType)}`;
  hero.querySelector('.hero-team:not(.right) .team-crest').textContent = match.teamA.shortName;
  hero.querySelector('.hero-team:not(.right) .hero-team-name').innerHTML = `${escapeHtml(match.teamA.name)}<small>${escapeHtml(match.teamA.shortName.toUpperCase())}</small>`;
  hero.querySelector('.hero-team.right .team-crest').textContent = match.teamB.shortName;
  hero.querySelector('.hero-team.right .hero-team-name').innerHTML = `${escapeHtml(match.teamB.name)}<small>${escapeHtml(match.teamB.shortName.toUpperCase())}</small>`;
  $('#hero-runs').textContent = batting.runs;
  $('#hero-wickets').textContent = batting.wickets;
  $('#hero-overs').textContent = oversText(batting);
  $('#hero-crr').textContent = crr;
  $('#hero-rrr').textContent = rrr;
  $('#hero-note').textContent = live && match.target ? `${runsNeeded} RUNS FROM ${ballsRemaining} BALLS` : match.result || match.venue;
  $('#selected-match-label').textContent = match.tournament;
  $('#player-stats-caption').textContent = `${match.teamA.name} vs ${match.teamB.name} · ${match.status.toLowerCase()}`;
  hero.querySelector('.venue').innerHTML = `⌖ ${escapeHtml(match.venue)} <b>·</b> ${escapeHtml(match.matchType)}`;
  hero.querySelector('.hero-stats').style.display = live ? 'flex' : 'none';
  hero.querySelector('.scorecard-button').style.display = match.status === 'UPCOMING' ? 'none' : '';
  document.querySelectorAll('.match-card').forEach((card) => card.classList.toggle('selected-card', card.dataset.matchId === selectedMatchId));
  renderPlayerStats();
}

async function selectMatch(id) {
  selectedMatchId = id;
  try {
    const response = await fetch(`${API}/matches/${encodeURIComponent(id)}`);
    if (!response.ok) throw new Error(`Could not load match (${response.status})`);
    const match = await response.json();
    matches = matches.map((item) => item.id === match.id ? match : item);
    renderMatches();
    renderFeatured();
    renderPerformers();
    renderMatchSummaries();
  } catch (error) {
    showToast(error.message);
  }
}

function renderPerformers() {
  const players = matches.flatMap((match) => match.players || []).filter((player) => activeTab === 'batting' ? player.role === 'Batter' : player.role === 'Bowler');
  const sorted = [...players].sort((a, b) => activeTab === 'batting' ? b.runs - a.runs : b.wickets - a.wickets);
  $('#performer-list').innerHTML = sorted.slice(0, 3).map((player, index) => {
    const score = activeTab === 'batting' ? `${player.runs}<small>${player.balls} balls · ${strikeRate(player)} SR</small>` : `${player.wickets}<small>wickets · ${player.economy.toFixed(1)} econ</small>`;
    return `<div class="performer-row"><span class="performer-rank">0${index + 1}</span><span class="performer-avatar">${escapeHtml(player.name.split(' ').map((part) => part[0]).join('').slice(0, 2))}</span><span class="performer-info"><strong>${escapeHtml(player.name)}</strong><span>${activeTab === 'batting' ? `${player.fours} fours · ${player.sixes} sixes` : 'Bowling'}</span></span><span class="performer-score">${score}</span></div>`;
  }).join('') || '<div class="loading-card">No player statistics available yet.</div>';
}

function renderPlayerStats() {
  const match = matches.find((item) => item.id === selectedMatchId);
  const container = $('#player-stats-table');
  if (!match) {
    container.innerHTML = '<div class="stats-empty">Select a match to see player statistics.</div>';
    return;
  }
  const role = statsTab === 'batting' ? 'Batter' : 'Bowler';
  const players = (match.players || []).filter((player) => player.role === role);
  if (players.length === 0) {
    const message = match.status === 'UPCOMING'
      ? 'Player statistics will be available when this match begins.'
      : 'Player-by-player figures are not available for this match yet.';
    container.innerHTML = `<div class="stats-empty"><span class="stats-empty-icon">▥</span><strong>No ${statsTab} figures yet</strong><span>${escapeHtml(message)}</span></div>`;
    return;
  }
  if (statsTab === 'batting') {
    container.innerHTML = `<table class="stats-table"><thead><tr><th>Batter</th><th>Status</th><th>R</th><th>B</th><th>4s</th><th>6s</th><th>SR</th></tr></thead><tbody>${players.map((player) => `<tr><td class="stats-player">${escapeHtml(player.name)}</td><td><span class="player-status">${player.onStrike ? 'On strike' : 'Batting'}</span></td><td class="number-cell">${player.runs}</td><td class="number-cell">${player.balls}</td><td class="number-cell">${player.fours}</td><td class="number-cell">${player.sixes}</td><td class="number-cell">${strikeRate(player)}</td></tr>`).join('')}</tbody></table>
      <div class="stats-footnote"><span><i class="strike-indicator"></i> On strike</span><span>SR = strike rate</span></div>`;
  } else {
    container.innerHTML = `<table class="stats-table"><thead><tr><th>Bowler</th><th>O</th><th>R</th><th>W</th><th>Economy</th></tr></thead><tbody>${players.map((player) => `<tr><td class="stats-player">${escapeHtml(player.name)}</td><td class="number-cell">${oversText({ballsFaced: player.balls})}</td><td class="number-cell">${player.conceded}</td><td class="number-cell">${player.wickets}</td><td class="number-cell">${player.economy.toFixed(2)}</td></tr>`).join('')}</tbody></table>
      <div class="stats-footnote"><span>O = overs</span><span>Economy = runs per over</span></div>`;
  }
}

function renderMatchSummaries() {
  $('#summary-count').textContent = `${matches.length} ${matches.length === 1 ? 'match' : 'matches'}`;
  const sorted = [...matches].sort((a, b) => statusOrder(a.status) - statusOrder(b.status));
  $('#summary-list').innerHTML = sorted.map((match) => {
    const live = match.status === 'LIVE';
    const upcoming = match.status === 'UPCOMING';
    const batting = match.battingTeam === match.teamA.name ? match.teamA : match.teamB;
    const ballsRemaining = Math.max(0, oversLimit(match.matchType) * 6 - batting.ballsFaced);
    const runsNeeded = Math.max(0, (match.target || 0) - batting.runs);
    let summary;
    if (live) {
      summary = match.target
        ? `${escapeHtml(batting.name)} need ${runsNeeded} runs from ${ballsRemaining} balls.`
        : `${escapeHtml(match.battingTeam)} are batting in the ${escapeHtml(match.matchType)} match.`;
    } else if (upcoming) {
      summary = `${escapeHtml(match.result || 'Match not started')} · ${escapeHtml(match.venue)}.`;
    } else {
      summary = escapeHtml(match.result || 'Match completed.');
    }
    return `<article class="summary-card${match.id === selectedMatchId ? ' summary-selected' : ''}" data-summary-match="${escapeHtml(match.id)}" tabindex="0" role="button" aria-label="View summary for ${escapeHtml(match.tournament)}">
      <div class="summary-card-top"><span class="summary-competition">${escapeHtml(match.tournament)}</span><span class="summary-status ${match.status.toLowerCase()}">${match.status}</span></div>
      <div class="summary-scoreline"><span>${escapeHtml(match.teamA.shortName)}</span><strong>${scoreLine(match.teamA)}</strong><span class="summary-versus">vs</span><span>${escapeHtml(match.teamB.shortName)}</span><strong>${scoreLine(match.teamB)}</strong></div>
      <p class="summary-copy">${summary}</p>
      <div class="summary-location"><span>⌖ ${escapeHtml(match.venue)}</span><span>${escapeHtml(match.matchType)}</span></div>
    </article>`;
  }).join('') || '<div class="loading-card">No match summaries available.</div>';
  document.querySelectorAll('.summary-card').forEach((card) => {
    const select = () => selectMatch(card.dataset.summaryMatch);
    card.addEventListener('click', select);
    card.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); select(); }
    });
  });
}

function scoreLine(team) {
  if (team.ballsFaced === 0) return '—';
  return `${team.runs}/${team.wickets} <small>(${oversText(team)})</small>`;
}

function oversLimit(matchType) {
  if (matchType === 'T20') return 20;
  if (matchType === 'ODI') return 50;
  return 90;
}

function strikeRate(player) {
  return player.balls ? (player.runs * 100 / player.balls).toFixed(1) : '0.0';
}

async function showScorecard() {
  const match = matches.find((item) => item.id === selectedMatchId);
  if (!match) return;
  try {
    const response = await fetch(`${API}/matches/${encodeURIComponent(match.id)}/scorecard`);
    if (!response.ok) throw new Error(`Could not load scorecard (${response.status})`);
    const scorecard = await response.json();
    const batters = scorecard.players.filter((player) => player.role === 'Batter');
    const bowlers = scorecard.players.filter((player) => player.role === 'Bowler');
    $('#scorecard-content').innerHTML = `<div class="dialog-result">${escapeHtml(scorecard.result || `${scorecard.battingTeam} need ${Math.max(0, (scorecard.target || 0) - currentBatting(scorecard).runs)} runs to win`)}</div>
      <section class="scorecard-section"><h3>Batting</h3><table class="scorecard-table"><thead><tr><th>Player</th><th>R</th><th>B</th><th>4s</th><th>6s</th><th>SR</th></tr></thead><tbody>${batters.map((player) => `<tr><td>${escapeHtml(player.name)}${player.onStrike ? ' *' : ''}</td><td>${player.runs}</td><td>${player.balls}</td><td>${player.fours}</td><td>${player.sixes}</td><td>${strikeRate(player)}</td></tr>`).join('')}</tbody></table></section>
      <section class="scorecard-section"><h3>Bowling</h3><table class="scorecard-table"><thead><tr><th>Player</th><th>O</th><th>R</th><th>W</th><th>ECON</th></tr></thead><tbody>${bowlers.map((player) => `<tr><td>${escapeHtml(player.name)}</td><td>${oversText({ballsFaced: player.balls})}</td><td>${player.conceded}</td><td>${player.wickets}</td><td>${player.economy.toFixed(1)}</td></tr>`).join('')}</tbody></table></section>
      <div class="scorecard-section"><h3>Recent balls</h3><div class="recent-balls">${scorecard.recentBalls.map((ball) => `<span class="mini-crest">${escapeHtml(ball)}</span>`).join(' ')}</div></div>`;
    $('#scorecard-dialog').showModal();
  } catch (error) {
    showToast(error.message);
  }
}

function currentBatting(match) {
  return match.battingTeam === match.teamA.name ? match.teamA : match.teamB;
}

function showToast(message) {
  const toast = $('#toast');
  toast.textContent = message;
  toast.classList.add('show');
  window.setTimeout(() => toast.classList.remove('show'), 2800);
}

function updateProfile(profile) {
  const cleanName = profile.name.trim();
  const firstName = cleanName.split(/\s+/)[0];
  const initials = cleanName.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase();
  const hour = new Date().getHours();
  const greeting = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
  $('#greeting-text').textContent = `${greeting}, ${firstName}`;
  $('#profile-name').textContent = cleanName;
  $('#profile-avatar').textContent = initials;
  $('#topbar-avatar').textContent = initials;
  document.body.dataset.accountEmail = profile.email;
}

function showDashboard(profile) {
  editingProfile = false;
  updateProfile(profile);
  document.body.classList.add('authenticated');
  $('#login-screen').classList.remove('editing');
  $('#login-screen').setAttribute('aria-hidden', 'true');
  $('.app-shell').setAttribute('aria-hidden', 'false');
}

function showLogin() {
  document.body.classList.remove('authenticated');
  $('#login-screen').classList.remove('editing');
  $('#login-screen').setAttribute('aria-hidden', 'false');
  $('.app-shell').setAttribute('aria-hidden', 'true');
  editingProfile = false;
  setAuthMode('register');
  $('#user-name').value = '';
  $('#user-email').value = '';
  $('#user-password').value = '';
  $('#login-error').textContent = '';
  window.scrollTo({top: 0, behavior: 'smooth'});
}

async function authRequest(path, payload) {
  const response = await fetch(`${API}/auth/${path}`, {
    method: path === 'profile' ? 'PATCH' : 'POST',
    headers: {'Content-Type': 'application/json'},
    credentials: 'same-origin',
    body: JSON.stringify(payload)
  });
  const body = await response.json();
  if (!response.ok) {
    throw new Error(body.detail || body.message || `Request failed (${response.status})`);
  }
  return body;
}

function setAuthMode(mode) {
  authMode = mode;
  editingProfile = false;
  $('#name-field').hidden = mode === 'login';
  $('#user-name').required = mode === 'register';
  $('#email-field').hidden = false;
  $('#user-email').required = true;
  $('#password-field').hidden = false;
  $('#user-password').required = true;
  $('#auth-switch-copy').hidden = false;
  $('#auth-switch-copy').innerHTML = mode === 'register'
    ? 'Already at the crease? <button class="auth-mode-link" id="auth-mode-toggle" type="button">Sign in</button>'
    : 'New to Boundary? <button class="auth-mode-link" id="auth-mode-toggle" type="button">Create account</button>';
  $('#login-title').innerHTML = mode === 'register' ? 'Create your<br>account.' : 'Welcome<br>back.';
  $('#login-description').textContent = mode === 'register'
    ? 'Your cricket dashboard, your name. Create an account to follow every ball.'
    : 'Sign in to pick up right where the game left off.';
  $('#login-submit').innerHTML = mode === 'register' ? 'Create account <span>→</span>' : 'Sign in <span>→</span>';
  $('#user-password').autocomplete = mode === 'register' ? 'new-password' : 'current-password';
  $('#login-screen').classList.remove('editing');
  $('#login-error').textContent = '';
}

function openNameScreen() {
  const loginScreen = $('#login-screen');
  editingProfile = true;
  loginScreen.classList.add('editing');
  loginScreen.setAttribute('aria-hidden', 'false');
  $('.app-shell').setAttribute('aria-hidden', 'true');
  $('#login-title').innerHTML = 'A fresh seat<br>in the stands.';
  $('#login-description').textContent = 'Update the name shown on your cricket dashboard.';
  $('#login-submit').innerHTML = 'Save my name <span>→</span>';
  $('#user-name').value = $('#profile-name').textContent;
  $('#name-field').hidden = false;
  $('#user-name').required = true;
  $('#email-field').hidden = true;
  $('#user-email').required = false;
  $('#password-field').hidden = true;
  $('#user-password').required = false;
  $('#auth-switch-copy').hidden = true;
  $('#login-error').textContent = '';
  document.body.classList.remove('authenticated');
  window.setTimeout(() => $('#user-name').focus(), 350);
}

function applyTheme(theme) {
  const isDark = theme === 'dark';
  document.body.classList.toggle('dark-mode', isDark);
  const toggle = $('#theme-toggle');
  toggle.setAttribute('aria-pressed', String(isDark));
  toggle.setAttribute('aria-label', `Switch to ${isDark ? 'light' : 'dark'} mode`);
  toggle.querySelector('.theme-icon').textContent = isDark ? '☀' : '☾';
  toggle.querySelector('.theme-label').textContent = isDark ? 'Light mode' : 'Dark mode';
  $('#login-theme-toggle').textContent = isDark ? '☀ Switch to light theme' : '◐ Theme follows you';
  document.querySelector('meta[name="theme-color"]').content = isDark ? '#111914' : '#f7f8f6';
}

const savedTheme = localStorage.getItem(THEME_KEY) || 'light';
applyTheme(savedTheme);
async function initializeAuth() {
  try {
    const response = await fetch(`${API}/auth/session`, {credentials: 'same-origin'});
    if (!response.ok) {
      if (response.status !== 401) throw new Error(`Could not check your session (${response.status})`);
      showLogin();
      return;
    }
    showDashboard(await response.json());
  } catch (error) {
    showLogin();
    $('#login-error').textContent = `${error.message}. Check that the server is running and try again.`;
  }
}

$('#login-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const name = $('#user-name').value.trim().replace(/\s+/g, ' ');
  const email = $('#user-email').value.trim();
  const password = $('#user-password').value;
  if ((authMode === 'register' || editingProfile) && !name) {
    $('#login-error').textContent = 'Please enter your name.';
    $('#user-name').focus();
    return;
  }
  const submit = $('#login-submit');
  submit.disabled = true;
  submit.innerHTML = 'Please wait… <span>◌</span>';
  $('#login-error').textContent = '';
  try {
    const profile = editingProfile
      ? await authRequest('profile', {name})
      : await authRequest(authMode, authMode === 'register' ? {name, email, password} : {email, password});
    showDashboard(profile);
  } catch (error) {
    $('#login-error').textContent = error.message;
  } finally {
    submit.disabled = false;
    if (!document.body.classList.contains('authenticated')) {
      submit.innerHTML = editingProfile
        ? 'Save my name <span>→</span>'
        : authMode === 'register' ? 'Create account <span>→</span>' : 'Sign in <span>→</span>';
    }
  }
});

$('#change-name').addEventListener('click', openNameScreen);
$('#change-name-sidebar').addEventListener('click', openNameScreen);
$('#password-toggle').addEventListener('click', () => {
  const input = $('#user-password');
  const visible = input.type === 'password';
  input.type = visible ? 'text' : 'password';
  $('#password-toggle').textContent = visible ? 'Hide' : 'Show';
  $('#password-toggle').setAttribute('aria-label', `${visible ? 'Hide' : 'Show'} password`);
});
$('#auth-switch-copy').addEventListener('click', (event) => {
  if (event.target.id === 'auth-mode-toggle') {
    setAuthMode(authMode === 'register' ? 'login' : 'register');
  }
});
$('#login-theme-toggle').addEventListener('click', () => {
  const nextTheme = document.body.classList.contains('dark-mode') ? 'light' : 'dark';
  localStorage.setItem(THEME_KEY, nextTheme);
  applyTheme(nextTheme);
});
$('#sign-out').addEventListener('click', async () => {
  try {
    const response = await fetch(`${API}/auth/logout`, {method: 'POST', credentials: 'same-origin'});
    if (!response.ok) throw new Error(`Could not sign out (${response.status})`);
    showLogin();
    $('#user-email').focus();
  } catch (error) {
    $('#login-error').textContent = error.message;
  }
});
$('#theme-toggle').addEventListener('click', () => {
  const nextTheme = document.body.classList.contains('dark-mode') ? 'light' : 'dark';
  localStorage.setItem(THEME_KEY, nextTheme);
  applyTheme(nextTheme);
});

function connectLiveUpdates() {
  if (!window.EventSource) return;
  eventSource = new EventSource(`${API}/live`);
  eventSource.addEventListener('initial-scores', (event) => {
    matches = JSON.parse(event.data);
    renderMatches();
    renderFeatured();
    renderPerformers();
    renderPlayerStats();
    renderMatchSummaries();
  });
  eventSource.addEventListener('score-update', (event) => {
    const updated = JSON.parse(event.data);
    matches = matches.map((match) => match.id === updated.id ? updated : match);
    renderMatches();
    renderFeatured();
    renderPerformers();
    renderPlayerStats();
    renderMatchSummaries();
  });
  eventSource.onerror = () => {
    eventSource.close();
    window.setTimeout(connectLiveUpdates, 5000);
  };
}

$('.scorecard-button').addEventListener('click', showScorecard);
$('.dialog-close').addEventListener('click', () => $('#scorecard-dialog').close());
$('#scorecard-dialog').addEventListener('click', (event) => {
  if (event.target === $('#scorecard-dialog')) $('#scorecard-dialog').close();
});
document.querySelectorAll('.performer-tab').forEach((tab) => tab.addEventListener('click', () => {
  activeTab = tab.dataset.tab;
  document.querySelectorAll('.performer-tab').forEach((item) => item.classList.toggle('active', item === tab));
  renderPerformers();
}));
document.querySelectorAll('[data-stats-tab]').forEach((tab) => tab.addEventListener('click', () => {
  statsTab = tab.dataset.statsTab;
  document.querySelectorAll('[data-stats-tab]').forEach((item) => item.classList.toggle('active', item === tab));
  renderPlayerStats();
}));
$('.mobile-menu').addEventListener('click', () => document.querySelector('.sidebar').classList.toggle('open'));
$('#today-date').textContent = new Intl.DateTimeFormat('en', {weekday: 'short', month: 'short', day: 'numeric'}).format(new Date());
loadMatches();
connectLiveUpdates();
initializeAuth();
window.setInterval(loadMatches, 30000);
