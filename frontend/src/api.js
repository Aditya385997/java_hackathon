const BASE = '/api/v1';

async function handle(res) {
  if (!res.ok) {
    // Surface the backend's ApiError.message verbatim; fall back to the status line.
    const err = await res.json().catch(() => ({ message: res.statusText }));
    throw new Error(err.message || 'Request failed');
  }
  return res.status === 204 ? null : res.json();
}

export const listPendingSuggestions = () =>
  fetch(`${BASE}/suggestions?status=PENDING`).then(handle);

export const decideSuggestion = (id, status) =>
  fetch(`${BASE}/suggestions/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status })
  }).then(handle);

export const setAgentStatus = (agentId, status) =>
  fetch(`${BASE}/agents/${agentId}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status })
  }).then(handle);
