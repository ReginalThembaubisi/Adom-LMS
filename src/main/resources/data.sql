-- Initialize Sequence Counter for 2026 Intake Seed Data
INSERT INTO learner_code_sequences (year_val, last_seq)
SELECT 2026, 0
WHERE NOT EXISTS (SELECT 1 FROM learner_code_sequences WHERE year_val = 2026);
