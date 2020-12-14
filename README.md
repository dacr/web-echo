# web-echo

web-echo is a very simple http service which allow you to POST json and GET it back as well as previously sent JSON.
One of its purposes it to help to test web hooks.

Usage example (when empty prefix is used) :

+ `curl -vL http://localhost:8080/`
  - It redirects you to an echo location such as :
    - `http://localhost:8080/echoed/08c65140-65d5-4363-8eac-287d6b8ac7c6`
+ Use this new location :  
  `curl http://localhost:8080/echoed/08c65140-65d5-4363-8eac-287d6b8ac7c6`
  - it will first give an empty response : `[]`
+ POST some data :  
  `curl -d '{"message":"hello"}' -H "Content-Type: application/json" http://localhost:8080/echoed/08c65140-65d5-4363-8eac-287d6b8ac7c6`
+ GET all sent data (ordered from latest to older) :  
  `curl http://localhost:8080/echoed/08c65140-65d5-4363-8eac-287d6b8ac7c6`
  - `[{"data":{"message":"hello"},"timestamp":"2020-12-07T14:10:17.871Z","client_ip":"127.0.0.1"}]`
+ GET some sent data (ordered from latest to older) :  
  `curl http://localhost:8080/echoed/08c65140-65d5-4363-8eac-287d6b8ac7c6?count=2`
+ GET latest sent data :  
  `curl http://localhost:8080/echoed/08c65140-65d5-4363-8eac-287d6b8ac7c6?latest`
+ GET some info about your echo :  
  `curl http://localhost:8080/echoed/08c65140-65d5-4363-8eac-287d6b8ac7c6/info`
