# Machine-to-Machine (M2M) Authentication with Keycloak

This guide explains how to configure and use Keycloak for Machine-to-Machine (M2M) authentication, allowing external software (agents, scripts, other services) to authenticate with **Web Echo** using dedicated credentials.

The standard approach for this is the **Client Credentials Grant** flow.

## 1. Keycloak Configuration

### Step 1: Create a Client
1. Log in to your Keycloak Admin Console.
2. Select your realm (e.g., `web-echo`).
3. Go to **Clients** and click **Create client**.
4. **General Settings**:
   - **Client ID**: `web-echo-m2m-agent` (or any name you prefer).
   - **Capability config**:
     - **Client authentication**: `On` (This generates a Client Secret).
     - **Authorization**: `Off` (unless specific authorization services are needed).
     - **Authentication flow**: Check **Service accounts roles** (This enables the `client_credentials` grant type).
     - Uncheck "Standard flow" and "Direct access grants" if this client is *strictly* for M2M (no user login).

### Step 2: Get Credentials
1. After creating the client, go to the **Credentials** tab.
2. Locate the **Client secret**. You will need this for the external software.

### Step 3: Configure Audience (Important)
Your Web Echo application validates the token's Audience (`aud`) or Authorized Party (`azp`) against the configured resource name (default: `web-echo`). 

Since the `azp` for a service account will match its own Client ID (e.g., `web-echo-m2m-agent`), you **must** map the audience so that the token is accepted by the Web Echo backend.

1. Go to the **Client scopes** tab of your new client.
2. Click on the dedicated scope link (usually named like `web-echo-m2m-agent-dedicated`).
3. Click **Add mapper** -> **By configuration**.
4. Select **Audience**.
5. Configure the mapper:
   - **Name**: `web-echo-audience-mapping`
   - **Included Client Audience**: Select the Client ID of your main Web Echo application (e.g., `web-echo`).
   - **Add to access token**: `On`.
6. Save.

## 2. Obtaining an Access Token

The remote machine (client) requests a token using the `client_credentials` grant type.

**Request:**

```bash
KEYCLOAK_URL="http://localhost:8081"
REALM_NAME="web-echo"
CLIENT_ID="web-echo-m2m-agent"
CLIENT_SECRET="<YOUR_CLIENT_SECRET>"

curl -s -X POST "$KEYCLOAK_URL/realms/$REALM_NAME/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=$CLIENT_ID" \
  -d "client_secret=$CLIENT_SECRET" \
  -d "grant_type=client_credentials"
```

**Response:**

You will receive a JSON response containing the `access_token`:

```json
{
  "access_token": "eyJhbGciOiJ...",
  "expires_in": 300,
  "refresh_expires_in": 0,
  "token_type": "Bearer",
  "not-before-policy": 0,
  "scope": "profile email"
}
```

## 3. Using the Token

Use the `access_token` in the `Authorization` header to make requests to the Web Echo API.

```bash
TOKEN="<PASTE_ACCESS_TOKEN_HERE>"
API_URL="http://localhost:8080/api/v2"

# Example: Create a recorder
curl -X POST "$API_URL/recorder" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```
