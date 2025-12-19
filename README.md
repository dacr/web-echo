# ![](images/logo-base-32.png) web-echo ![tests][tests-workflow] [![License][licenseImg]][licenseLink] [![][WebEchoImg]][WebEchoLnk]

The web-echo service is an immutable JSON data recording service.

This service allows you to easily define on the fly JSON recorders which can be
fed by remote services using either the dynamically generated webhook or the websockets
URL you've provided. Then, at any time you can check/get what has been sent by the
remote services.

The storage implementation is using a blockchain approach to ensure data integrity; when
data has been sent to the service, it always provides back a receipt proof.

## Quick local start

Use [scala-cli][scl] to download and start a web-echo service on port 8080 with
API documentation on http://127.0.0.1:8080/docs :

```
scala-cli --dep fr.janalyse::web-echo:2.1.0 -e 'webecho.Main.main(args)'
```

## Quick test

Quick test once the web-service is started and running on port 8080

### Testing with webhooks

```bash
ENDPOINT=http://127.0.0.1:8080/api/v2
# create a new recorder and get its ID
ID=$(curl -X POST $ENDPOINT/recorder  -H 'accept: application/json' | jq -r .id)

# simulate some data sent by a remote service
curl -s "$ENDPOINT/recorder/$ID?name=joe&age=42"  | jq
curl -s -X PUT $ENDPOINT/recorder/$ID -H 'accept: application/json' -d '{"name":"jane","age":24}' | jq
curl -s -X POST $ENDPOINT/recorder/$ID -H 'accept: application/json' -d '{"name":"john","age":12}' | jq

# then check the recorded content
curl -s "$ENDPOINT/recorder/$ID/records" | jq
```


### Testing with websockets

First, starts a simple websocket server that just send ticks :
```bash
scala-cli basic-wss-server.sc -- 8888
# OR
scala-cli https://raw.githubusercontent.com/dacr/web-echo/refs/heads/master/basic-wss-server.sc -- 8888
```

Then 
```bash
ENDPOINT=http://127.0.0.1:8080/api/v2

# create a new recorder and get its ID
ID=$(curl -X POST $ENDPOINT/recorder  -H 'accept: application/json' | jq -r .id)

# then register the websocket server to the recorder
curl -s -X POST $ENDPOINT/recorder/$ID/websocket -H 'accept: application/json' -d '{
  "uri": "ws://127.0.0.1:8888",
  "expire": "60 seconds"
}'

# then check the recorded content
curl -s "$ENDPOINT/recorder/$ID/records?limit=10" | jq .data.tick
```

## General configuration

| Environment variable                 | Description                                | default value              |
|--------------------------------------|--------------------------------------------|----------------------------|
| WEB_ECHO_LISTEN_IP                   | Listening network interface                | "0.0.0.0"                  |
| WEB_ECHO_LISTEN_PORT                 | Listening port                             | 8080                       |
| WEB_ECHO_PREFIX                      | Add a prefix to all defined routes         | ""                         |
| WEB_ECHO_URL                         | How this service is known from outside     | "http://127.0.0.1:8080"    |
| WEB_ECHO_STORE_PATH                  | Where data are stored                      | "/tmp/web-echo-cache-data" |
| WEB_ECHO_WEBSOCKETS_DEFAULT_DURATION | Default duration for websockets connection | 15m                        |
| WEB_ECHO_WEBSOCKETS_MAX_DURATION     | Maximum duration for websockets connection | 4h                         |
| WEB_ECHO_SHA_GOAL                    | Difficulty level for Proof of Work (0=off) | 0                          |


## Security

By default, security is disabled. You can enable Keycloak authentication to protect recorder creation.

### Configuration

| Environment variable                 | Description                                  | Default value         |
|--------------------------------------|----------------------------------------------|-----------------------|
| WEB_ECHO_SECURITY_KEYCLOAK_ENABLED   | Enable Keycloak authentication               | false                 |
| WEB_ECHO_SECURITY_KEYCLOAK_URL       | Keycloak server URL                          | "http://localhost:8081" |
| WEB_ECHO_SECURITY_KEYCLOAK_REALM     | Keycloak realm                               | "web-echo"            |
| WEB_ECHO_SECURITY_KEYCLOAK_RESOURCE  | Keycloak client ID / resource                | "web-echo"            |

### Usage with Authentication

When security is enabled, you must provide a valid JWT token in the `Authorization` header to create a recorder.

```bash
# Get a token from Keycloak (example)
TOKEN=$(curl -s -X POST "http://localhost:8081/realms/web-echo/protocol/openid-connect/token" \
  -d "client_id=web-echo" \
  -d "username=$WEB_ECHO_USERNAME" \
  -d "password=$WEB_ECHO_PASSWORD" \
  -d "grant_type=password" | jq -r .access_token)

# Create a recorder using the token
ID=$(curl -s -X POST $ENDPOINT/recorder \
  -H "Authorization: Bearer $TOKEN" \
  -H 'accept: application/json' | jq -r .id)
```

[scl]: https://scala-cli.virtuslab.org/

[webecho-api]: https://web-echo.code-examples.org/docs

[WebEcho]: https://github.com/dacr/web-echo

[WebEchoImg]: https://img.shields.io/maven-central/v/fr.janalyse/web-echo_3.svg

[WebEchoLnk]: https://search.maven.org/artifact/fr.janalyse/web-echo_3

[tests-workflow]: https://github.com/dacr/web-echo/actions/workflows/scala.yml/badge.svg

[licenseImg]: https://img.shields.io/github/license/dacr/web-echo.svg

[licenseLink]: LICENSE
