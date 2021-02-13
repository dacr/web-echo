# ![](images/logo-base-32.png) web-echo

web-echo service allows you to easily define on the fly JSON recorders which can be 
fed by remote services using either the dynamically generated webhook or the websocket (soon)
you've provided. Then, at any time you can check what have been sent by the configured
remote services. [Check the swagger API specification](https://mapland.fr/echo/swagger) for more information.

## Quick local start

Thanks to [coursier](https://get-coursier.io/) from @alxarchambault,
a web-echo service is quite simple to start, just execute :
```
cs launch fr.janalyse::web-echo:1.1.0
```

## Configuration

| Environment variable | Description                                    | default value
| -------------------- | ---------------------------------------------- | -----------------
| WEB_ECHO_LISTEN_IP   | Listening network interface                    | "0.0.0.0"
| WEB_ECHO_LISTEN_PORT | Listening port                                 | 8080
| WEB_ECHO_PREFIX      | Add a prefix to all defined routes             | ""   
| WEB_ECHO_URL         | How this service is known from outside         | "http://127.0.0.1:8080" 
