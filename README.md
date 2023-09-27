
# api-platform-inbound-soap

This service receives SOAP XML requests from the EU's CCN2 system and processes them according to their content.
Messages can be broadly split into 3 types:
 - Confirmation: these are forwarded to ```api-platform-outbound-soap```
 - With embedded file: these have the embedded file removed and sent to SDES for virus scanning before forwarding to  ```import-control-inbound-proxy```
 - Everything else: forwarded on to  ```import-control-inbound-proxy```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").

### Request validation
Bad requests will be met with:
```
<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
    <soap:Header xmlns:soap="http://www.w3.org/2003/05/soap-envelope"></soap:Header>
    <soap:Body>
        <soap:Fault>
            <soap:Code>
                <soap:Value>soap:Sender</soap:Value>
            </soap:Code>
            <soap:Reason>
                <soap:Text xml:lang="en">Argument description too short</soap:Text>
            </soap:Reason>
            <soap:Node>public-soap-proxy</soap:Node>
            <soap:Detail>
                <RequestId>eebb1d86-afc2-4223-a064-07a246c36619</RequestId>
            </soap:Detail>
        </soap:Fault>
    </soap:Body>
</soap:Envelope>
```
with content-type header containing :
```
content-type: application/soap+xml
```