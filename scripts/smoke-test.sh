#!/usr/bin/env bash
set -euo pipefail

# Simple smoke test for the Storefront system via the API Gateway
# Requires python3 and curl

GATEWAY_URL="http://localhost:8080"
JWT_SECRET="change-me-to-a-strong-secret"

# Generate a UUID for user and address
USER_ID=$(python3 - <<'PY'
import uuid,sys
print(str(uuid.uuid4()))
PY
)
ADDRESS_ID=$(python3 - <<'PY'
import uuid,sys
print(str(uuid.uuid4()))
PY
)

# Generate JWT token (HS256) using the same secret as the gateway
TOKEN=$(python3 - <<PY
import jwt, datetime, uuid, sys
secret = sys.argv[1]
sub = sys.argv[2]
exp = datetime.datetime.utcnow() + datetime.timedelta(minutes=10)
payload = {"sub": sub, "role": "USER", "exp": int(exp.timestamp())}
print(jwt.encode(payload, secret, algorithm="HS256"))
PY "${JWT_SECRET}" "${USER_ID}")

echo "Generated userId=${USER_ID}, token=${TOKEN}"

# List products (public endpoint)
echo "Fetching product list..."
curl -s -X GET "$GATEWAY_URL/api/products" -H "Accept: application/json"

# Create an order
ORDER_PAYLOAD=$(cat <<EOF
{
  "userId": "${USER_ID}",
  "addressId": "${ADDRESS_ID}",
  "items": [
    {
      "productId": "$(python3 - <<'PY'
import uuid,sys; print(str(uuid.uuid4()))
PY)",
      "quantity": 1,
      "price": 100.00
    }
  ]
}
EOF
)

echo "Creating order..."
CREATE_RESPONSE=$(curl -s -X POST "$GATEWAY_URL/api/orders" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d "$ORDER_PAYLOAD")
ORDER_ID=$(echo "$CREATE_RESPONSE" | tr -d '"')
echo "Order created with ID: $ORDER_ID"

# Poll order status until CONFIRMED or CANCELLED
echo "Polling order status..."
while true; do
    STATUS_RESPONSE=$(curl -s -X GET "$GATEWAY_URL/api/orders/$ORDER_ID" \
        -H "Authorization: Bearer $TOKEN")
    # Extract status from JSON (assuming field "status")
    STATUS=$(echo "$STATUS_RESPONSE" | python3 -c "import sys,json;obj=json.load(sys.stdin);print(obj.get('status',''))")
    echo "Current status: $STATUS"
    if [[ "$STATUS" == "CONFIRMED" || "$STATUS" == "CANCELLED" ]]; then
        echo "Final status: $STATUS"
        break
    fi
    sleep 5
done

# Print order timeline (if available via deliveries endpoint)
echo "Fetching delivery timeline..."
curl -s -X GET "$GATEWAY_URL/api/deliveries/$ORDER_ID/timeline" \
    -H "Authorization: Bearer $TOKEN" | python3 -m json.tool || true

echo "Smoke test completed."
