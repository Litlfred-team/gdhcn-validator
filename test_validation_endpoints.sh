#!/bin/bash

# Test script for the unified validation API
# Run this after starting the web application

BASE_URL="http://localhost:8080"

# Sample QR codes for testing
HCERT_QR='HC1:6BF+70790T9WJWG.FKY*4GO0RKPNHILCJ$VBFBBRW1*70HS8FN0WLCD/OWY01BCEKID97TK0F90KECTHGWJC0FDC:5AIA%G7X+AQB9746HS80:5AIBZ47VF6$A8VL6+0AP7BN46/46R46 463ZACN9+Y9 +AVK4WJCT3EHS8XJC$+DXJCCWENF6OF63W5NW6UF6%JC QE/IAYJC5LEW34U3ET7DXC9 QE-ED8%E.JCBECB1A-:8$966469L6OF6VX6Q$D.UDRYA 96NF6L/5SW6Y57KQEPD09WEQDD+Q6TW6FA7C466KCN9E%961A6DL6FA7D46JPCT3E5JDNA7$Q68465W51S6..DX%DZJC5/DL+8 VD 8D%$EWKEI3D:-C-3EWED1ECW.CBWEY*82T92694ZAVIAI3D8WE Y9G+95N9$PC5$CUZCY$5Y$527B789C%HQWK8E5%H4ZL2S/RS*UX51KUV:Q07WOK1DHBQZVQLTR4:1LPUR%CT M$MAW.85BNXQNF:JN408DJRP0HX48878CJD9K8BD4:F'

SHC_QR='shc:/56762909524320603460292437404460312229595326546034602925407728043360287028647167452228092861333145643765314159064022030645045908564355034142454136403706366541713724123638030437562204673740753232392543344332605736010645292953127074242843503861221276716852752941725536670334373625647345380024213944077025250726312423573657001132105220316267750968640761356508111008270666243020277044446712214341455936637024282703544034660963252707282555072932056232255262395660612010735336331255715610420057716412306973057066214536651135113958591233120032575026733958333075072812533734264534700060266054734545664338772667663471584128617435526828390065275357404052057121004150076600323056277610287226003175060305765803534256207472564464060539095425076777272921345209305565332021506258456045760350722804223710051277402927664527742911662372066523664321240336446744622769760467573259652733383263657311072452563376417025746807407539006144613252696869456340066810522645386256555532111000531265754227302628303438085756243800662563286838775672222439672172403542396107375860647335106645704512536703506321757004413636764365347431287321355256580631556063583463563610567737660541737377552828605563116564297412076854033003344323337052606873573426066033102439280977115921594064314334576408722871427337224310757744412937522268303871367257627564750472597763507745283571571263580550066921715611703323062474012471272931363924743604256803437445104259400424433362673769543855403976310365501153573745056364696326060377575776050561075823064353055604551028500311453022452525062305534574'

UNKNOWN_QR='UNKNOWN:12345'

echo "Testing Unified Validation API Endpoint"
echo "======================================"

# Test 1: Valid HCERT QR Code - String Content
echo -e "\n1. Testing valid HCERT QR code (string content)..."
response=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/validate-code" \
  -H "Content-Type: application/json" \
  -d "{\"content\": \"$HCERT_QR\"}")

http_code="${response: -3}"
body="${response%???}"

echo "HTTP Status: $http_code"
echo "Response: $body" | jq '.' 2>/dev/null || echo "$body"

# Test 2: Valid HCERT QR Code with format parameter
echo -e "\n2. Testing valid HCERT QR code with format parameter..."
response=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/validate-code?format=HC1" \
  -H "Content-Type: application/json" \
  -d "{\"content\": \"$HCERT_QR\"}")

http_code="${response: -3}"
body="${response%???}"

echo "HTTP Status: $http_code"
echo "Response: $body" | jq '.' 2>/dev/null || echo "$body"

# Test 3: Non-HC1 QR Code (should be rejected for now)
echo -e "\n3. Testing non-HC1 QR code (SHC) - should be rejected..."
response=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/validate-code" \
  -H "Content-Type: application/json" \
  -d "{\"content\": \"$SHC_QR\"}")

http_code="${response: -3}"
body="${response%???}"

echo "HTTP Status: $http_code"
echo "Response: $body" | jq '.' 2>/dev/null || echo "$body"

# Test 4: Wrong format parameter
echo -e "\n4. Testing HCERT with wrong format parameter..."
response=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/validate-code?format=SHC" \
  -H "Content-Type: application/json" \
  -d "{\"content\": \"$HCERT_QR\"}")

http_code="${response: -3}"
body="${response%???}"

echo "HTTP Status: $http_code"
echo "Response: $body" | jq '.' 2>/dev/null || echo "$body"

# Test 5: Unsupported format parameter
echo -e "\n5. Testing unsupported format parameter..."
response=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/validate-code?format=UNKNOWN" \
  -H "Content-Type: application/json" \
  -d "{\"content\": \"$HCERT_QR\"}")

http_code="${response: -3}"
body="${response%???}"

echo "HTTP Status: $http_code"
echo "Response: $body" | jq '.' 2>/dev/null || echo "$body"

# Test 6: Unknown QR format
echo -e "\n6. Testing unknown QR format..."
response=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/validate-code" \
  -H "Content-Type: application/json" \
  -d "{\"content\": \"$UNKNOWN_QR\"}")

http_code="${response: -3}"
body="${response%???}"

echo "HTTP Status: $http_code"
echo "Response: $body" | jq '.' 2>/dev/null || echo "$body"

# Test 7: Missing content
echo -e "\n7. Testing missing content..."
response=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/validate-code" \
  -H "Content-Type: application/json" \
  -d "{}")

http_code="${response: -3}"
body="${response%???}"

echo "HTTP Status: $http_code"
echo "Response: $body" | jq '.' 2>/dev/null || echo "$body"

# Test 8: Image upload test (commented out - requires actual image file)
echo -e "\n8. Image upload test (requires QR code image)..."
echo "# To test image upload, use:"
echo "# curl -X POST \"$BASE_URL/validate-code\" -F \"file=@path/to/qr_image.png\""

# Test 9: Service info endpoint 
echo -e "\n9. Testing service info endpoint..."
response=$(curl -s -w "%{http_code}" -X GET "$BASE_URL/")

http_code="${response: -3}"
body="${response%???}"

echo "HTTP Status: $http_code"
echo "Response: $body" | jq '.' 2>/dev/null || echo "$body"

echo -e "\n======================================"
echo "Unified validation API testing completed!"
echo ""
echo "Expected HTTP Status Codes:"
echo "  200 - Valid certificate"
echo "  400 - Invalid input format"
echo "  404 - QR code format not recognized"
echo "  422 - Invalid certificate content"