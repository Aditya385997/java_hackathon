import { useEffect, useState } from 'react';
import { listTasks, createTask } from './api';

export default function App() {
  const [tasks, setTasks] = useState([]);
  const [title, setTitle] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const refresh = () =>
    listTasks().then(setTasks).catch(e => setError(e.message)).finally(() => setLoading(false));

  useEffect(() => { refresh(); }, []);

  const submit = async () => {
    try {
      setError(null);
      await createTask(title);
      setTitle('');
      refresh();
    } catch (e) {
      setError(e.message);
    }
  };

  return (
    <main style={{ maxWidth: 640, margin: '48px auto', fontFamily: 'system-ui' }}>
      <h1>Tasks</h1>
      <div style={{ display: 'flex', gap: 8, marginBottom: 24 }}>
        <input
          value={title}
          onChange={e => setTitle(e.target.value)}
          placeholder="New task"
          style={{ flex: 1, padding: 8 }}
        />
        <button onClick={submit} style={{ padding: '8px 16px' }}>Add</button>
      </div>
      {error && <p style={{ color: 'crimson' }}>{error}</p>}
      {loading ? <p>Loading…</p> : (
        <ul>
          {tasks.map(t => (
            <li key={t.id}>{t.title} — {t.status}</li>
          ))}
        </ul>
      )}
    </main>
  );
}