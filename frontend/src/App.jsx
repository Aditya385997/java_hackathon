import { useCallback, useEffect, useState } from 'react';
import { listPendingSuggestions, decideSuggestion } from './api';

const POLL_MS = 4000;

export default function App() {
  const [suggestions, setSuggestions] = useState([]);
  const [error, setError] = useState(null);
  const [busyId, setBusyId] = useState(null);
  const [loaded, setLoaded] = useState(false);

  const refresh = useCallback(async () => {
    try {
      setSuggestions(await listPendingSuggestions());
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoaded(true);
    }
  }, []);

  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, POLL_MS);
    return () => clearInterval(timer);
  }, [refresh]);

  const decide = async (id, status) => {
    setBusyId(id);
    try {
      await decideSuggestion(id, status);
      await refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusyId(null);
    }
  };

  return (
    <main style={S.page}>
      <h1 style={S.h1}>Pending reassignments</h1>
      <p style={S.sub}>
        Raised by routing — manually or automatically when an agent goes OFFLINE.
        Nothing moves until you accept. Refreshes every {POLL_MS / 1000}s.
      </p>

      {error && <p style={S.error}>{error}</p>}

      {!loaded ? (
        <p>Loading…</p>
      ) : suggestions.length === 0 ? (
        <p style={S.empty}>No pending suggestions.</p>
      ) : (
        <table style={S.table}>
          <thead>
            <tr>
              {['Order', 'Recommended', 'Confidence', 'Trigger', 'Strategy', 'Reasoning', ''].map(h => (
                <th key={h} style={S.th}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {suggestions.map(s => (
              <tr key={s.id}>
                <td style={S.td}><code>{s.orderId}</code></td>
                <td style={S.td}><code>{s.recommendedAgentId}</code></td>
                <td style={S.td}>{s.confidence}</td>
                <td style={S.td}>
                  <span style={s.triggerReason === 'AGENT_OFFLINE' ? S.tagAlert : S.tag}>
                    {s.triggerReason}
                  </span>
                </td>
                <td style={S.td}>
                  <span style={s.strategyUsed === 'ai' ? S.tagAi : S.tag}>{s.strategyUsed}</span>
                </td>
                <td style={{ ...S.td, ...S.reasoning }}>{s.reasoning}</td>
                <td style={{ ...S.td, whiteSpace: 'nowrap' }}>
                  <button
                    style={S.accept}
                    disabled={busyId === s.id}
                    onClick={() => decide(s.id, 'ACCEPTED')}
                  >
                    Accept
                  </button>
                  <button
                    style={S.reject}
                    disabled={busyId === s.id}
                    onClick={() => decide(s.id, 'REJECTED')}
                  >
                    Reject
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </main>
  );
}

const S = {
  page: { maxWidth: 1100, margin: '40px auto', padding: '0 16px', fontFamily: 'system-ui, sans-serif' },
  h1: { marginBottom: 4 },
  sub: { color: '#555', marginTop: 0, fontSize: 14 },
  error: { background: '#fdecea', border: '1px solid #f5c2c0', color: '#8a1c15', padding: '8px 12px', borderRadius: 4 },
  empty: { color: '#666', fontStyle: 'italic' },
  table: { width: '100%', borderCollapse: 'collapse', fontSize: 14 },
  th: { textAlign: 'left', borderBottom: '2px solid #ddd', padding: '8px 6px', color: '#444' },
  td: { borderBottom: '1px solid #eee', padding: '8px 6px', verticalAlign: 'top' },
  reasoning: { maxWidth: 380, color: '#333', lineHeight: 1.4 },
  tag: { background: '#eef1f5', padding: '2px 6px', borderRadius: 3, fontSize: 12 },
  tagAi: { background: '#e6f0ff', color: '#0b4ea2', padding: '2px 6px', borderRadius: 3, fontSize: 12 },
  tagAlert: { background: '#fff2e0', color: '#94500a', padding: '2px 6px', borderRadius: 3, fontSize: 12 },
  accept: { marginRight: 6, padding: '5px 12px', cursor: 'pointer' },
  reject: { padding: '5px 12px', cursor: 'pointer' }
};
