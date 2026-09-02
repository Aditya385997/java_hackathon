-- ─────────────────────────────────────────────────────────
-- Hackathon · Seed Data (Addendum A, official fixture — do not modify)
-- H2 profile only; the postgres profile is owned by Flyway.
-- ─────────────────────────────────────────────────────────

-- 5 agents: mix of AVAILABLE and BUSY
INSERT INTO agents (id, name, active_order_count, status) VALUES
  ('AGT-001', 'Priya Sharma',  2, 'BUSY'),
  ('AGT-002', 'Rahul Verma',   0, 'AVAILABLE'),
  ('AGT-003', 'Ananya Iyer',   1, 'BUSY'),
  ('AGT-004', 'Kiran Nair',    0, 'AVAILABLE'),
  ('AGT-005', 'Deepak Mehta',  3, 'BUSY');

-- 8 orders: all ASSIGNED, spread across agents
INSERT INTO orders (id, description, assigned_agent_id, status, created_at) VALUES
  ('ORD-001', 'Electronics — Koramangala to Indiranagar', 'AGT-001', 'ASSIGNED', NOW()),
  ('ORD-002', 'Groceries — HSR Layout to BTM',            'AGT-001', 'ASSIGNED', NOW()),
  ('ORD-003', 'Pharma — Whitefield to Marathahalli',      'AGT-003', 'ASSIGNED', NOW()),
  ('ORD-004', 'Documents — MG Road to Jayanagar',         'AGT-005', 'ASSIGNED', NOW()),
  ('ORD-005', 'Food — Bellandur to Electronic City',      'AGT-005', 'ASSIGNED', NOW()),
  ('ORD-006', 'Apparel — Malleshwaram to Rajajinagar',    'AGT-005', 'ASSIGNED', NOW()),
  ('ORD-007', 'Books — Banashankari to JP Nagar',         'AGT-003', 'ASSIGNED', NOW()),
  ('ORD-008', 'Hardware — Peenya to Yeshwanthpur',        'AGT-001', 'ASSIGNED', NOW());
