import calendar
import re
from datetime import date
from pathlib import Path

import pytest

from jellyfin_mcp.namedays import MONTHS, name_day

KOTLIN_TABLE = (
    Path(__file__).resolve().parents[3]
    / "app/src/main/java/com/github/damontecres/wholphin/ui/components/NameDays.kt"
)


def test_table_has_all_days_of_a_leap_year():
    assert sum(len(m) for m in MONTHS) == 366
    assert [len(m) for m in MONTHS] == [calendar.monthrange(2024, i)[1] for i in range(1, 13)]


def test_spot_checks():
    assert name_day(date(2026, 10, 13)) == "Koloman"
    assert name_day(date(2026, 12, 24)) == "Adam, Eva"
    assert "Alexandra" in name_day(date(2026, 1, 2))
    assert name_day(date(2028, 2, 29)) == "Radomír"
    assert name_day(date(2026, 12, 26)) == "Štefan"
    assert name_day(date(2026, 1, 1)) is None
    assert name_day(date(2026, 12, 25)) is None


@pytest.mark.skipif(not KOTLIN_TABLE.exists(), reason="the app sources are not next to the server")
def test_same_as_app_table():
    source = KOTLIN_TABLE.read_text(encoding="utf-8")
    table = source[source.index("private val table") :]
    app_days = re.findall(r'^\s*"([^"]*)",', table, re.MULTILINE)
    assert app_days == [names for month in MONTHS for names in month]
