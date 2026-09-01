const BASE = '/api/v1/tasks';

async function handle(res) {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: res.statusText }));
    throw new Error(err.message || 'Request failed');
  }
  return res.status === 204 ? null : res.json();
}

export const listTasks = () => fetch(BASE).then(handle);

export const createTask = (title) =>
  fetch(BASE, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title })
  }).then(handle);

export const updateStatus = (id, status) =>
  fetch(`${BASE}/${id}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status })
  }).then(handle);