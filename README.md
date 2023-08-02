
# api-platform-inbound-soap

This service receives SOAP XML requests from the EU's CCN2 system and processes them according to their content.
Messages can be broadly split into 3 types:
 - Confirmation: these are forwarded to ```api-platform-outbound-soap```
 - With embedded file: these have the embedded file removed and sent to SDES for virus scanning before forwarding to  ```import-control-inbound-proxy```
 - Everything else: forwarded on to  ```import-control-inbound-proxy```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").