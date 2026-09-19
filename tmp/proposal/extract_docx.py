import zipfile
import re
from pathlib import Path

docx = Path(r"c:\Users\ADMIN\Downloads\SU26SE096_SUB_LEASING_MANAGEMENT_SYSTEM_khanhkt.docx")
out = Path(r"f:\Capstone\slms2026\tmp\proposal\proposal.txt")
out.parent.mkdir(parents=True, exist_ok=True)

with zipfile.ZipFile(docx) as z:
    xml = z.read("word/document.xml").decode("utf-8")

text = re.sub(r"</w:p>", "\n", xml)
text = re.sub(r"<[^>]+>", "", text)
text = re.sub(r"[ \t]+\n", "\n", text)
text = re.sub(r"\n{3,}", "\n\n", text)
replacements = {
    "&amp;": "&",
    "&lt;": "<",
    "&gt;": ">",
    "&#8211;": "-",
    "&#8220;": '"',
    "&#8221;": '"',
}
for a, b in replacements.items():
    text = text.replace(a, b)

out.write_text(text, encoding="utf-8")
print("len", len(text))
lines = [l.strip() for l in text.splitlines() if l.strip()]
print("total lines", len(lines))
for i, l in enumerate(lines):
    print(f"{i}: {l[:160]}")
