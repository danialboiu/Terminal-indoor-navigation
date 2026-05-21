#!/usr/bin/env zsh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
MAP_FILE="$ROOT_DIR/src/main/resources/terminal-map.json"
OUT_DIR="$ROOT_DIR/generated/qrs"
BASE_URL="${QR_BASE_URL:-http://localhost:8080}"

usage() {
  cat <<EOF
Usage:
  ./generate_qr.sh --list
  ./generate_qr.sh "<label-or-node-id>"

Examples:
  ./generate_qr.sh --list
  ./generate_qr.sh "Entrance C"
  ./generate_qr.sh F0_ENT_C

Optional:
  QR_BASE_URL="https://your-domain.com" ./generate_qr.sh "Entrance C"

Output:
  generated/qrs/from_<NODE_ID>.png
EOF
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_command node
require_command swiftc

if [[ ! -f "$MAP_FILE" ]]; then
  echo "Missing terminal map: $MAP_FILE" >&2
  exit 1
fi

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

if [[ "${1:-}" == "--list" ]]; then
  node - "$MAP_FILE" <<'NODE'
const fs = require('fs')
const mapPath = process.argv[2]
const map = JSON.parse(fs.readFileSync(mapPath, 'utf8'))
const nodes = (map.nodes || [])
  .filter((node) => node && node.enabled !== false && node.selectableFrom !== false)
  .sort((a, b) => (a.label || a.id).localeCompare(b.label || b.id))

console.log('Label\tNode ID')
for (const node of nodes) {
  console.log(`${node.label || node.id}\t${node.id}`)
}
NODE
  exit 0
fi

QUERY="${1:-}"
if [[ -z "$QUERY" ]]; then
  usage
  echo
  echo "Selectable labels:"
  "$0" --list
  exit 1
fi

NODE_JSON="$(node - "$MAP_FILE" "$QUERY" <<'NODE'
const fs = require('fs')
const mapPath = process.argv[2]
const query = process.argv[3].trim().toLowerCase()
const map = JSON.parse(fs.readFileSync(mapPath, 'utf8'))
const nodes = (map.nodes || [])
  .filter((node) => node && node.enabled !== false && node.selectableFrom !== false)

const exact = nodes.find((node) =>
  String(node.id || '').toLowerCase() === query ||
  String(node.label || '').toLowerCase() === query
)

const partial = exact || nodes.find((node) =>
  String(node.label || '').toLowerCase().includes(query)
)

if (!partial) {
  console.error(`Unknown label or node id: ${process.argv[3]}`)
  console.error('Run ./generate_qr.sh --list to see available labels.')
  process.exit(1)
}

console.log(JSON.stringify({
  id: partial.id,
  label: partial.label || partial.id
}))
NODE
)"

NODE_ID="$(node -e 'console.log(JSON.parse(process.argv[1]).id)' "$NODE_JSON")"
NODE_LABEL="$(node -e 'console.log(JSON.parse(process.argv[1]).label)' "$NODE_JSON")"
FROM_PARAM="$(node -e 'console.log(encodeURIComponent(process.argv[1]))' "$NODE_ID")"
QR_URL="${BASE_URL%/}/?from=$FROM_PARAM"
OUT_FILE="$OUT_DIR/from_${NODE_ID}.png"

mkdir -p "$OUT_DIR"

SWIFT_FILE="$(mktemp /tmp/terminal_qr.XXXXXX.swift)"
SWIFT_BIN="$(mktemp /tmp/terminal_qr.XXXXXX)"
trap 'rm -f "$SWIFT_FILE" "$SWIFT_BIN"' EXIT

cat > "$SWIFT_FILE" <<'SWIFT'
import AppKit
import CoreImage
import CoreImage.CIFilterBuiltins
import Foundation

guard CommandLine.arguments.count == 3 else {
    fputs("Usage: swift qr.swift <payload> <output.png>\n", stderr)
    exit(1)
}

let payload = CommandLine.arguments[1]
let outputPath = CommandLine.arguments[2]

let context = CIContext()
let filter = CIFilter.qrCodeGenerator()
filter.message = Data(payload.utf8)
filter.correctionLevel = "M"

guard let qrImage = filter.outputImage else {
    fputs("Failed to generate QR image.\n", stderr)
    exit(1)
}

let scaledImage = qrImage.transformed(by: CGAffineTransform(scaleX: 12, y: 12))
guard let cgImage = context.createCGImage(scaledImage, from: scaledImage.extent) else {
    fputs("Failed to render QR image.\n", stderr)
    exit(1)
}

let bitmap = NSBitmapImageRep(cgImage: cgImage)
guard let pngData = bitmap.representation(using: .png, properties: [:]) else {
    fputs("Failed to encode QR image as PNG.\n", stderr)
    exit(1)
}

try pngData.write(to: URL(fileURLWithPath: outputPath))
SWIFT

swiftc "$SWIFT_FILE" -o "$SWIFT_BIN" -framework AppKit -framework CoreImage
"$SWIFT_BIN" "$QR_URL" "$OUT_FILE"

echo "Generated QR for: $NODE_LABEL ($NODE_ID)"
echo "Payload: $QR_URL"
echo "File: $OUT_FILE"
