# Sample Data

Real RingConn wearable export data (Jan–Jun 2026), anonymised by removing the device nickname from filenames. The health metrics themselves are genuine.

## Files

| File | Content |
|------|---------|
| `Activity-2026-01-01-2026-06-29.csv` | Daily steps and calories |
| `Sleep-2026-01-01-2026-06-29.csv` | Sleep sessions with stages (REM / Light / Deep) |
| `Vital Signs-2026-01-01-2026-06-29.csv` | Daily heart rate, SpO2, HRV averages |

## How to Import

1. Start the backend (`./mvnw spring-boot:run`)
2. Login to the Android app with `demo@wellness.app / password123`
3. Go to the **Import** tab
4. Tap **Pick Files**, select all three CSVs (or select them all at once)
5. Tap **Start Import**

You can also import via curl:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@wellness.app","password":"password123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

curl -s -X POST http://localhost:8080/api/wellness/import \
  -H "Authorization: Bearer $TOKEN" \
  -F "files=@Activity-2026-01-01-2026-06-29.csv" \
  -F "files=@Sleep-2026-01-01-2026-06-29.csv" \
  -F "files=@Vital Signs-2026-01-01-2026-06-29.csv"
```
