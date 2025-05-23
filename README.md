# ![](images/logo-base-32.png) web-echo ![tests][tests-workflow] [![License][licenseImg]][licenseLink] [![][WebEchoImg]][WebEchoLnk]

web-echo service allows you to easily define on the fly JSON recorders which can be
fed by remote services using either the dynamically generated webhook or the websockets
you've provided. Then, at any time you can check what have been sent by the configured
remote services. [Check the swagger API specification][webecho-api] for more information.

It has been deployed on https://mapland.fr/echo

## Quick local start

Thanks to [scala-cli][scl],
this application is quite easy to start, just execute :

```
scala-cli --dep fr.janalyse::web-echo:1.2.6 -e 'webecho.Main.main(args)'
```

## Configuration

| Environment variable                | Description                                    | default value              |
|-------------------------------------|------------------------------------------------|----------------------------|
| WEB_ECHO_LISTEN_IP                  | Listening network interface                    | "0.0.0.0"                  |
| WEB_ECHO_LISTEN_PORT                | Listening port                                 | 8080                       |
| WEB_ECHO_PREFIX                     | Add a prefix to all defined routes             | ""                         |
| WEB_ECHO_URL                        | How this service is known from outside         | "http://127.0.0.1:8080"    |
| WEB_ECHO_STORE_PATH                 | Where data is stored                           | "/tmp/web-echo-cache-data" |
| WEB_ECHO_INACTIVE_AUTO_DELETE       | Delete echoes if inactive                      | true                       |
| WEB_ECHO_INACTIVE_AUTO_DELETE_AFTER | Delay for an echo to become inactive           | 30d                        |
| WEB_ECHO_ENTRIES_MAX_QUEUE_SIZE     | Limit the number of entries, older are removed | 1000                       |

[cs]: https://get-coursier.io/

[scl]: https://scala-cli.virtuslab.org/

[webecho-api]: https://mapland.fr/echo/swagger

[WebEcho]: https://github.com/dacr/web-echo

[WebEchoImg]: https://img.shields.io/maven-central/v/fr.janalyse/web-echo_2.13.svg

[WebEchoLnk]: https://search.maven.org/#search%7Cga%7C1%7Cfr.janalyse.web-echo

[tests-workflow]: https://github.com/dacr/web-echo/actions/workflows/scala.yml/badge.svg

[licenseImg]: https://img.shields.io/github/license/dacr/web-echo.svg

[licenseLink]: LICENSE
