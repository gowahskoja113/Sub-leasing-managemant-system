const fs = require("fs");
const path = require("path");
const { execSync } = require("child_process");
const os = require("os");

const docx = String.raw`c:\Users\ADMIN\Downloads\SU26SE096_SUB_LEASING_MANAGEMENT_SYSTEM_khanhkt.docx`;
const outDir = String.raw`f:\Capstone\slms2026\tmp\proposal`;
const extractDir = path.join(outDir, "docx_unzip");
fs.mkdirSync(extractDir, { recursive: true });

// Expand via PowerShell
execSync(
  `powershell -NoProfile -Command "Expand-Archive -LiteralPath '${docx.replace(/'/g, "''")}' -DestinationPath '${extractDir.replace(/'/g, "''")}' -Force"`,
  { stdio: "inherit" }
);

// docx is zip - Expand-Archive might fail on .docx? Try copy to .zip first if needed
let xmlPath = path.join(extractDir, "word", "document.xml");
if (!fs.existsSync(xmlPath)) {
  const zipCopy = path.join(outDir, "proposal.zip");
  fs.copyFileSync(docx, zipCopy);
  execSync(
    `powershell -NoProfile -Command "Expand-Archive -LiteralPath '${zipCopy.replace(/'/g, "''")}' -DestinationPath '${extractDir.replace(/'/g, "''")}' -Force"`,
    { stdio: "inherit" }
  );
  xmlPath = path.join(extractDir, "word", "document.xml");
}

let xml = fs.readFileSync(xmlPath, "utf8");
let text = xml.replace(/<\/w:p>/g, "\n").replace(/<[^>]+>/g, "");
text = text
  .replace(/&amp;/g, "&")
  .replace(/&lt;/g, "<")
  .replace(/&gt;/g, ">")
  .replace(/&#8211;/g, "-")
  .replace(/&#8220;/g, '"')
  .replace(/&#8221;/g, '"')
  .replace(/[ \t]+\n/g, "\n")
  .replace(/\n{3,}/g, "\n\n");

fs.writeFileSync(path.join(outDir, "proposal.txt"), text, "utf8");
const lines = text.split(/\n/).map((l) => l.trim()).filter(Boolean);
console.log("len", text.length, "lines", lines.length);
lines.forEach((l, i) => console.log(`${i}: ${l.slice(0, 180)}`));
