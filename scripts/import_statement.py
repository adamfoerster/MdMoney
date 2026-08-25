#!/usr/bin/env python3
"""One-time import of a bank statement (xlsx) into MdMoney ledger notes.

Every row becomes one purchase in `<vault>/<account>/<year> <Mon> - <group>.md`, so a month of
coffees is a single note rather than a file each. The output must match what the app itself writes
(see data/LedgerMapper.kt): the same frontmatter keys in the same order, `category:` and `account:`
as quoted wikilinks into their metadata notes, the same padded table, newest row first, and amounts
formatted with a dot and no trailing zeros.

Expected sheet columns: Date | Description | Category | Valor (USD)
  - Date is tolerated as a real date cell or as m/d/yyyy text.
  - Amounts are negative (money out) and are stored positive.
  - Category becomes the group (the note's title and file name); CATEGORY_SLUG maps it to the
    category note that `category:` points at, and a category with no note yet gets one, so no link
    this writes ever dangles.

Usage:
    python3 scripts/import_statement.py <statement.xlsx> <vault> <account> [--dry-run]

Never overwrites: it merges into an existing note, skipping rows already present and preserving the
frontmatter keys it doesn't manage, a link title tuned in Obsidian, and any prose around the table.
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

CATEGORIES_FOLDER = "categories"

# Group (as it appears in the statement) -> the category note it links to, English and lowercase.
# The slug is the category's identity (its file name in `categories/`); the group name is only the
# title the link displays.
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


def prettify_slug(slug):
    """`adam shopping` -> `Adam Shopping`, like domain/Category.kt — the last-resort link title."""
    words = [w for w in slug.split(" ") if w.strip()]
    return " ".join(w[0].upper() + w[1:] for w in words) or slug


# --- frontmatter ---------------------------------------------------------------------------------
#
# A note is an ordered list of frontmatter entries plus the body after the closing `---`. Entries
# are ("scalar", key, value) or ("raw", key, lines) — the second holds block lists and anything else
# this script doesn't understand, kept verbatim and in place. That round-trip is the whole point:
# the vault is the user's own Markdown, and an importer that drops a key it didn't recognise (or the
# prose someone wrote under the table) destroys data the app promises to preserve. Mirrors
# data/FrontmatterParser.kt and data/MarkdownNote.kt.


def top_level_colon(line):
    if not line or line[0].isspace() or line[0] == "-":
        return -1
    return line.find(":")


def parse_entries(fm_lines):
    entries = []
    i = 0
    while i < len(fm_lines):
        line = fm_lines[i]
        if not line.strip():
            i += 1
            continue
        colon = top_level_colon(line)
        if colon < 0:
            entries.append(("raw", "", [line]))  # not a key line; keep it verbatim
            i += 1
            continue
        key, rest = line[:colon].strip(), line[colon + 1:]
        if rest.strip():
            entries.append(("scalar", key, rest.strip()))
            i += 1
            continue
        # Empty value: gather the indented continuation lines (a block list, say).
        block = [line]
        j = i + 1
        while j < len(fm_lines) and fm_lines[j] and fm_lines[j][0].isspace():
            block.append(fm_lines[j])
            j += 1
        entries.append(("raw", key, block) if len(block) > 1 else ("scalar", key, ""))
        i = j
    return entries


def parse_note(content):
    """Returns (entries, body). A note with no frontmatter is all body, so nothing is lost."""
    text = (content or "").replace("\r\n", "\n")
    lines = text.split("\n")
    if not lines or lines[0].strip() != "---":
        return [], text
    closing = next((i for i in range(1, len(lines)) if lines[i].strip() == "---"), -1)
    if closing < 0:
        return [], text
    body = "\n".join(lines[closing + 1:]) if closing + 1 < len(lines) else ""
    return parse_entries(lines[1:closing]), body


def serialize(entries, body):
    out = ["---"]
    for entry in entries:
        if entry[0] == "scalar":
            _, key, value = entry
            out.append(f"{key}: {value}" if value else f"{key}:")
        else:
            out.extend(entry[2])
    out.append("---")
    return "\n".join(out) + "\n" + body


def index_of(entries, key):
    return next((i for i, e in enumerate(entries) if e[1] == key), -1)


def scalar(entries, key):
    for entry in entries:
        if entry[0] == "scalar" and entry[1] == key:
            return entry[2].strip() or None
    return None


def set_scalar(entries, key, value, after=None):
    """Sets key in place when present; otherwise adds it after `after`, or at the end."""
    rendered = (value or "").strip()
    idx = index_of(entries, key)
    if idx >= 0:
        entries[idx] = ("scalar", key, rendered)
        return
    anchor = index_of(entries, after) if after else -1
    if anchor >= 0:
        entries.insert(anchor + 1, ("scalar", key, rendered))
    else:
        entries.append(("scalar", key, rendered))


def parse_vault_link(raw):
    """(target, title) from `"[[food|Alimentação]]"`, tolerating no quotes, a path, or plain text."""
    text = (raw or "").strip()
    if len(text) >= 2 and text[0] == text[-1] and text[0] in "\"'":
        text = text[1:-1].strip()
    if not text:
        return None
    if not (text.startswith("[[") and text.endswith("]]")):
        return (text, None)  # the plain `food` that predates links resolves to the same category
    target, _, title = text[2:-2].partition("|")
    target = target.strip().rsplit("/", 1)[-1]  # Obsidian may write a path when the name isn't unique
    return (target, title.strip() or None) if target else None


def format_vault_link(target, title):
    """Quoted, because Obsidian reads a bare `[[x]]` in a property as a list rather than a link."""
    return f'"[[{target}|{title}]]"'


def set_link(entries, key, target, title, after=None):
    """
    Points key at target — and leaves a link that already resolves exactly as written, so a title
    tuned in Obsidian (`"[[food|Nossa comida]]"`) survives a re-import. A plain legacy value with no
    link at all is upgraded, which is the migration. Mirrors setLink in data/VaultLinks.kt.
    """
    if not target:
        if index_of(entries, key) >= 0:
            set_scalar(entries, key, None)
        return
    current = parse_vault_link(scalar(entries, key))
    if current and current[0] == target and current[1]:
        return
    set_scalar(entries, key, format_vault_link(target, title), after=after)


# --- table ---------------------------------------------------------------------------------------


def split_row(line):
    t = line.strip()
    if not t.startswith("|"):
        return None
    return [c.strip() for c in t.strip("|").split("|")]


def read_rows(body):
    """The (date, note, amount) rows already in the note, so a re-run is a no-op."""
    rows = []
    for line in body.split("\n"):
        cells = split_row(line)
        if not cells or len(cells) < 3:
            continue
        try:
            amount = float(cells[2].replace(",", "."))
        except ValueError:
            continue  # header or separator
        rows.append((cells[0], cells[1], amount))
    return rows


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


def replace_table(body, table):
    """Swaps the run of table lines in body for table, leaving prose before or after it untouched."""
    lines = body.replace("\r\n", "\n").split("\n")
    first = next((i for i, line in enumerate(lines) if split_row(line) is not None), -1)
    if first < 0:
        prose = body.rstrip("\n")
        return "\n" + table if not prose.strip() else prose + "\n\n" + table
    last = first
    while last + 1 < len(lines) and split_row(lines[last + 1]) is not None:
        last += 1
    before = "" if first == 0 else "\n".join(lines[:first]) + "\n"
    after = "\n".join(lines[last + 1:])
    return before + table.rstrip("\n") + ("\n" if not after else "\n" + after)


# --- notes ---------------------------------------------------------------------------------------


def read_file(path):
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as f:
        return f.read()


def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    # newline="\n" so the files stay LF on Windows too, like the ones the app writes.
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)


def category_title(vault, slug, fallback):
    """The title a link to `categories/<slug>.md` displays: the note's own, else the group name."""
    note = read_file(os.path.join(vault, CATEGORIES_FOLDER, f"{slug}.md"))
    if note is None:
        return fallback or prettify_slug(slug)
    return scalar(parse_note(note)[0], "title") or fallback or prettify_slug(slug)


def account_title(vault, account):
    """The title `account:` links display, from `<vault>/<Account>.md`; the folder is the identity."""
    note = read_file(os.path.join(vault, f"{account}.md"))
    return (scalar(parse_note(note)[0], "title") if note else None) or account


def ensure_category(vault, slug, title, dry_run):
    """Creates `categories/<slug>.md` when it's missing. An existing note is the user's — untouched."""
    path = os.path.join(vault, CATEGORIES_FOLDER, f"{slug}.md")
    if os.path.exists(path):
        return False
    if not dry_run:
        # `description:` is written bare rather than omitted, so the field waits in Obsidian's
        # property editor — same as CategoryMeta.create.
        write_file(path, f"---\ntype: category\ntitle: {title}\ndescription:\n---\n")
    return True


def render_note(existing, title, category, cat_title, account, acc_title, year, month_idx, rows):
    """The merged note: managed keys rewritten, everything else in the file left as it was."""
    entries, body = parse_note(existing)
    set_scalar(entries, "title", title)
    set_link(entries, "category", category, cat_title)
    # `conta:` is the legacy plain-text ancestor of `account:`: left exactly as the user wrote it
    # where it exists (its casing may differ from the folder), never added to a new note.
    set_link(entries, "account", account, acc_title, after="conta")
    set_scalar(entries, "year", str(year))
    set_scalar(entries, "month", MONTHS[month_idx - 1])
    set_scalar(entries, "total", format_amount(sum(a for _, _, a in rows)))
    # Newest first; equal dates keep their statement order (stable sort), like the app does.
    ordered = sorted(rows, key=lambda e: e[0], reverse=True)
    return serialize(entries, replace_table(body, render_table(ordered)))


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
    acc_title = account_title(args.vault, args.account)

    # Give every category a note before linking to it, exactly as the app does before a write.
    created = []
    for group in sorted({g for _, _, g in groups}):
        slug = CATEGORY_SLUG.get(group)
        if slug and ensure_category(args.vault, slug, group, args.dry_run):
            created.append(slug)

    written = 0
    uncategorised = set()
    for (year, month_idx, group), rows in sorted(groups.items()):
        title = sanitize_title(group)
        name = f"{year} {MONTHS[month_idx - 1][:3]} - {title}.md"
        path = os.path.join(out_dir, name)
        slug = CATEGORY_SLUG.get(group)
        if not slug:
            uncategorised.add(group)

        existing_content = read_file(path)
        existing = read_rows(parse_note(existing_content)[1]) if existing_content else []
        seen = {(d, n, format_amount(a)) for d, n, a in existing}
        fresh = [r for r in rows if (r[0], r[1], format_amount(r[2])) not in seen]
        merged = existing + fresh
        content = render_note(
            existing_content, title,
            slug, category_title(args.vault, slug, group) if slug else None,
            args.account, acc_title,
            year, month_idx, merged,
        )

        total = sum(a for _, _, a in merged)
        note = "" if not existing else f"  (merged into {len(existing)} existing, {len(rows) - len(fresh)} dup skipped)"
        print(f"{name:38} {len(merged):3} entries  total {total:9.2f}{note}")
        if not args.dry_run:
            write_file(path, content)
            written += 1

    print(f"\n{count} rows -> {len(groups)} notes" + ("" if args.dry_run else f", {written} written to {out_dir}"))
    if created:
        verb = "would create" if args.dry_run else "created"
        print(f"{verb} {len(created)} category note(s) in {CATEGORIES_FOLDER}/: {', '.join(created)}")
    if uncategorised:
        print(f"no CATEGORY_SLUG for: {', '.join(sorted(uncategorised))} — written without a category")


if __name__ == "__main__":
    main()
