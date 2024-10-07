
# api-platform-inbound-soap

This service receives SOAP XML requests from the EU's CCN2 system and processes them according to their content.
Messages can be broadly split into 3 types:
 - Confirmation: these are forwarded to ```api-platform-outbound-soap```
 - With embedded file: these have the embedded file removed and sent to SDES for virus scanning before forwarding to  ```import-control-inbound-proxy```
 - Everything else: forwarded on to  ```import-control-inbound-proxy```

XML request bodies received at the CCN2 controller are validated by a filter to ensure that various parts of the body are present and meet expected criteria mainly related to length. 
### Licence

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").

### Sample cURL requests
For 200 response (you'll need to alter the path to the XML file in the `-d` argument, according to where you have the project checked out):
```
curl -v localhost:9000/api-platform-inbound-soap/ics2/NESReferralBASV2  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3Mjc5NjY4ODd9.cSKzno8ytgA8-C5kIg_0NSVF-Ar48fJ9_1jnygbYuGM" -H "Content-Type: application/soap+xml" -d @/<your projects directory>/api-platform-inbound-soap/test/resources/ie4r02-v2.xml
``` 
One example of a bad request is:
```
curl -v localhost:9000/api-platform-inbound-soap/ics2/NESReferralBASV2  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3Mjc5NjY4ODd9.cSKzno8ytgA8-C5kIg_0NSVF-Ar48fJ9_1jnygbYuGM" -H "x-request-id: cc901cd5-3348-4713-8a6b-4c803e308dc1" -H "Content-Type: application/soap+xml" -d @/<your projects directory>/api-platform-inbound-soap/test/resources/ie4r02-v2-missing-description-element.xml
```
which will result in a response like:
```
<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
    <soap:Header xmlns:soap="http://www.w3.org/2003/05/soap-envelope"></soap:Header>
    <soap:Body>
        <soap:Fault>
            <soap:Code>
                <soap:Value>soap:400</soap:Value>
            </soap:Code>
            <soap:Reason>
                <soap:Text xml:lang="en">Argument description is too short</soap:Text>
            </soap:Reason>
            <soap:Node>public-soap-proxy</soap:Node>
            <soap:Detail>
                <RequestId>cc901cd5-3348-4713-8a6b-4c803e308dc1</RequestId>
            </soap:Detail>
        </soap:Fault>
    </soap:Body>
</soap:Envelope>
```
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