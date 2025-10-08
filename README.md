
# api-platform-inbound-soap

This service receives SOAP XML requests from the EU's CCN2 system and processes them according to their content. It services
three areas of import-control concern: ICS2, CRDL and CERTEX.

ICS2 is the most complex, having messages that can be broadly split into 3 types:
 - Confirmation: these are forwarded to ```api-platform-outbound-soap```
 - With embedded file: these have the embedded file removed and sent to SDES for virus scanning. 
SDES returns a UUID in response to receiving a file to scan and this replaces the embedded file in the message forwarded to  ```import-control-inbound-proxy```
 - Everything else: forwarded unchanged to  ```import-control-inbound-proxy```

ICS2 XML request bodies received at the controller are validated by a filter to ensure that various parts of the body are present and meet expected criteria mainly related to length. 

CRDL has, for the purposes of this service, 2 message types:
 - those with attachments 
 - those without

As for ICS2, those with attachments have the embedded file removed and sent to SDES. 
Both message types are forwarded to ```central-reference-data-inbound-orchestrator```. 

CERTEX also has, for the purposes of this service, 2 message types:
- those with attachments
- those without

Again those with attachments have the embedded file removed and sent to SDES.

Both message types are forwarded, via proxy, to a service outside of MDTP.



### Licence

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").

### Sample cURL requests
For a successful 2xx response (you'll need to alter the path to the XML file in the `-d` argument, according to where you have the project checked out):
```
curl -v localhost:6707/ics2/NESReferralBASV2  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJjM2E5YTEwMS05MzdiLTQ3YzEtYmMzNS1iZGIyNGIxMmU0ZTUiLCJleHAiOjIwNTU0MTQ5NzN9.T2tTGStmVttHtj2Hruk5N1yh4AUyPVuy6t5d-gH0tZU" -H "Content-Type: application/soap+xml" -d @/<your projects directory>/api-platform-inbound-soap/test/resources/ie4n09-v2.xml
``` 
One example of a bad request is:
```
curl -v localhost:6707/ics2/NESReferralBASV2  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJjM2E5YTEwMS05MzdiLTQ3YzEtYmMzNS1iZGIyNGIxMmU0ZTUiLCJleHAiOjIwNTU0MTQ5NzN9.T2tTGStmVttHtj2Hruk5N1yh4AUyPVuy6t5d-gH0tZU" -H "x-request-id: cc901cd5-3348-4713-8a6b-4c803e308dc1" -H "Content-Type: application/soap+xml" -d @/<your projects directory>/api-platform-inbound-soap/test/resources/ie4r02-v2-missing-description-element.xml
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
Note that requests are accompanied by an `Authorization: Bearer` header. This must be a JWT with the correct issuer 
and a valid expiry date. Confluence has instructions on generating an appropriate JWT, in the CCN2 Gateway page.
### Request validation
Bad requests will be met with a SOAP response detailing the problem e.g.:
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

### SDES integration
Where an incoming message is found to contain a complex element named `binaryAttachment` or `binaryFile` then the embedded binary
data is sent to SDES for quarantining. It will be easier to understand this by reference to the following chunk of XML:
```
<urn:binaryAttachment>
      <urn:filename>test-filename.txt</urn:filename>
      <urn:MIME>text/plain</urn:MIME>
      <urn:includedBinaryObject>dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVyIHRoZSBsYXp5IGRvZwo=</urn:includedBinaryObject>
      <urn:description>A text file containing some text</urn:description>
</urn:binaryAttachment>
```

The embedded binary data is the value of the element named `includedBinaryObject`. This Base 64 encoded string is sent to SDES, accompanied
by a metadata header, and SDES returns a UUID in its response.
The value of the `includedBinaryObject` is replaced with the SDES UUID and the message is then forwarded to the configured 
recipient service. Note that for ICS2, the UUID is itself Base 64 encoded before being inserted in the message. This is
probably not necessary as a UUID wouldn't contain any characters that could violate an XML schema, but is the behaviour
that is expected by the consumer of ICS2 messages `import-control-inbound-soap`.

### Running on its own
The service can be started locally using sbt. It will start on the same port as it does when started using Service Manager (6707) 
if you execute ```sbt run```

### Running the tests
There's a selection of unit and integration tests which can be run by executing:
```
sbt run-all-tests
```
When ready to commit it's advisable to run:
```
sbt pre-commit
```
as this formats the code and generates a coverage report. Test coverage below the configured level will cause build 
failures locally and on Jenkins.

### Running with Service Manager
Executing the shell script ```run_local_with_dependencies.sh``` will start the service along with the services it depends upon.
It will start on port 6707 and will forward all requests on to the instance of ```api-platform-test``` started by Service Manager.
After executing the "For a successful 2xx response" cURL request provided above you can inspect what payload this service has forwarded
by viewing the  logs of ```api-platform-test```.

This can be done by executing
```sm2 --logs API_PLATFORM_TEST``` and looking for a line beginning ```Received notification with payload```.