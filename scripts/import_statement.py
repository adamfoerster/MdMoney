#!/usr/bin/env python3
"""One-time import of a bank statement (xlsx) into MdMoney ledger notes.

Every row becomes one purchase in `<vault>/<account>/<year> <Mon> - <group>.md`, so a month of
coffees is a single note rather than a file each. The output must match what the app itself writes
(see data/LedgerMapper.kt): same frontmatter order, same padded table, newest row first, and amounts
formatted with a dot and no trailing zeros.

Expected sheet columns: Date | Description | Category | Valor (USD)
  - Date is tolerated as a real date cell or as m/d/yyyy text.
  - Amounts are negative (money out) and are stored positive.
  - Category becomes the group (the note's title and file name); CATEGORY_SLUG maps it to the
    frontmatter `category`.

Usage:
    python3 scripts/import_statement.py <statement.xlsx> <vault> <account> [--dry-run]

Never overwrites: it merges into an existing note, skipping rows already present.
"""

import argparse
import datetime
import os
import re
import sys
from collections import defaultdict

try:
    import openpyxl
except ImportError:
    sys.exit("openpyxl is required: pip3 install --user openpyxl")

MONTHS = [
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
]

# Group (as it appears in the statement) -> frontmatter `category`, English and lowercase.
CATEGORY_SLUG = {
    "Alimentação": "food",
    "Casa": "home",
    "Carro": "car",
    "Compras Adam": "adam shopping",
    "Mercado": "groceries",
    "Hospedagem": "lodging",
    "Internet": "internet",
    "Parques": "parks",
    "Luz": "electricity",
    "Saúde": "health",
}


def format_amount(value):
    """Mirrors data/Amounts.kt formatAmount: at most two decimals, trailing zeros trimmed."""
    negative = value < 0
    cents = int(round(abs(value) * 100))
    whole, rem = divmod(cents, 100)
    sign = "-" if negative and cents != 0 else ""
    if rem == 0:
        return f"{sign}{whole}"
    if rem % 10 == 0:
        return f"{sign}{whole}.{rem // 10}"
    return f"{sign}{whole}.{rem:02d}"


def parse_date(value):
    if isinstance(value, datetime.datetime):
        return value.date()
    if isinstance(value, datetime.date):
        return value
    if isinstance(value, str):
        m, d, y = value.strip().split("/")
        return datetime.date(int(y), int(m), int(d))
    raise ValueError(f"unrecognised date: {value!r}")


def sanitize_title(title):
    return re.sub(r'[\\/:*?"<>|]', "-", title.strip()).strip()


def render_table(entries):
    """entries: list of (date_str, note, amount). Newest first, columns padded."""
    rows = [[d, n, format_amount(a)] for d, n, a in entries]
    header = ["Date", "Note", "Amount"]
    widths = [max(len(r[i]) for r in rows + [header]) for i in range(3)]

    def row(cells):
        return "| " + " | ".join(c.ljust(widths[i]) for i, c in enumerate(cells)) + " |"

    out = [row(header), "| " + " | ".join("-" * w for w in widths) + " |"]
    out += [row(r) for r in rows]
    return "\n".join(out) + "\n"


def read_existing(path):
    """Returns the (date, note, amount) rows already in a ledger note, so a re-run is a no-op."""
    if not os.path.exists(path):
        return []
    entries = []
    for line in open(path, encoding="utf-8").read().split("\n"):
        t = line.strip()
        if not t.startswith("|"):
            continue
        cells = [c.strip() for c in t.strip("|").split("|")]
        if len(cells) < 3:
            continue
        try:
            amount = float(cells[2].replace(",", "."))
        except ValueError:
            continue  # header or separator
        entries.append((cells[0], cells[1], amount))
    return entries


def render_note(title, category, account, year, month_idx, entries):
    total = format_amount(sum(a for _, _, a in entries))
    # Newest first; equal dates keep their statement order (stable sort), like the app does.
    ordered = sorted(entries, key=lambda e: e[0], reverse=True)
    fm = [
        f"title: {title}",
        f"category: {category}" if category else "category:",
        f"conta: {account}",
        f"year: {year}",
        f"month: {MONTHS[month_idx - 1]}",
        f"total: {total}",
    ]
    return "---\n" + "\n".join(fm) + "\n---\n\n" + render_table(ordered)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("xlsx")
    ap.add_argument("vault")
    ap.add_argument("account")
    ap.add_argument("--sheet", default=None)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    wb = openpyxl.load_workbook(args.xlsx, data_only=True)
    ws = wb[args.sheet] if args.sheet else wb.worksheets[0]

    groups = defaultdict(list)
    count = 0
    for raw in list(ws.iter_rows(values_only=True))[1:]:
        if not raw or all(v is None for v in raw[:4]):
            continue
        date, description, category, amount = raw[0], raw[1], raw[2], raw[3]
        if amount is None:
            continue
        d = parse_date(date)
        key = (d.year, d.month, str(category).strip())
        groups[key].append((d.strftime("%Y%m%d"), str(description).strip(), abs(float(amount))))
        count += 1

    out_dir = os.path.join(args.vault, args.account)
    if not args.dry_run:
        os.makedirs(out_dir, exist_ok=True)

    written = 0
    for (year, month_idx, group), rows in sorted(groups.items()):
        title = sanitize_title(group)
        name = f"{year} {MONTHS[month_idx - 1][:3]} - {title}.md"
        path = os.path.join(out_dir, name)

        existing = read_existing(path)
        fresh = [r for r in rows if r not in existing]
        merged = existing + fresh
        content = render_note(title, CATEGORY_SLUG.get(group, ""), args.account, year, month_idx, merged)

        total = sum(a for _, _, a in merged)
        note = "" if not existing else f"  (merged into {len(existing)} existing, {len(rows) - len(fresh)} dup skipped)"
        print(f"{name:38} {len(merged):3} entries  total {total:9.2f}{note}")
        if not args.dry_run:
            with open(path, "w", encoding="utf-8") as f:
                f.write(content)
            written += 1

    print(f"\n{count} rows -> {len(groups)} notes" + ("" if args.dry_run else f", {written} written to {out_dir}"))


if __name__ == "__main__":
    main()
