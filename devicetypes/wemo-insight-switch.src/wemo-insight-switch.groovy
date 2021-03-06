/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * Wemo Switch
 *
 * Author: Juan Risso (SmartThings)
 * Date: 2015-10-11
 */
 metadata {
 	definition (name: "WeMo Insight Switch", namespace: "wemo", author: "SmartThings") {
        capability "Actuator"
        capability "Switch"
        capability "Polling"
        capability "Refresh"
        capability "Sensor"

        attribute "currentIP", "string"

        command "subscribe"
        command "resubscribe"
        command "unsubscribe"
        command "setOffline"
 }

 // simulator metadata
 simulator {}

 // UI tile definitions
    tiles(scale: 2) {
        multiAttributeTile(name:"rich-control", type: "switch", canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                 attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.off", backgroundColor:"#79b821", nextState:"turningOff"
                 attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.on", backgroundColor:"#ffffff", nextState:"turningOn"
                 attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.switch.off", backgroundColor:"#79b821", nextState:"turningOff"
                 attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.switch.on", backgroundColor:"#ffffff", nextState:"turningOn"
                 attributeState "offline", label:'${name}', icon:"st.switches.switch.off", backgroundColor:"#ff0000"
 			}
            tileAttribute ("currentIP", key: "SECONDARY_CONTROL") {
             	 attributeState "currentIP", label: ''
 			}
        }

        standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
            state "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.off", backgroundColor:"#79b821", nextState:"turningOff"
            state "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.on", backgroundColor:"#ffffff", nextState:"turningOn"
            state "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.switch.off", backgroundColor:"#79b821", nextState:"turningOff"
            state "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.switch.on", backgroundColor:"#ffffff", nextState:"turningOn"
            state "offline", label:'${name}', icon:"st.switches.switch.off", backgroundColor:"#ff0000"
        }
		valueTile("energy", "device.energy",height: 2, width: 2, decoration: "flat") {
            state "default", label:'${currentValue} KWH'
        }
        valueTile("power", "device.power", height: 2, width: 2, decoration: "flat") {
            state "default", label:'${currentValue}\nWatts'
        }
        valueTile("onToday", "device.onToday", height: 2, width: 2, decoration: "flat") {
            state "default", label:'${currentValue}\nMin Today'
        }
        standardTile("refresh", "device.switch", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        main(["switch"])
        details(["rich-control", "power","onToday","refresh"])
    }
}

// parse events into attributes
def parse(String description) {

    def msg = parseLanMessage(description)
    def headerString = msg.header
	log.debug "Parsing '${msg}'"
    if (headerString?.contains("SID: uuid:")) {
        def sid = (headerString =~ /SID: uuid:.*/) ? ( headerString =~ /SID: uuid:.*/)[0] : "0"
        sid -= "SID: uuid:".trim()

        updateDataValue("subscriptionId", sid)
 	}

    def result = []
    def bodyString = msg.body
    if (bodyString) {
        try {
            unschedule("setOffline")
        } catch (e) {
            log.error "unschedule(\"setOffline\")"
        }
        def body = new XmlSlurper().parseText(bodyString)
 		if (body?.property?.TimeSyncRequest?.text()) {
        	log.trace "Got TimeSyncRequest"
        	result << timeSyncResponse()
        } else if (body?.Body?.SetBinaryStateResponse?.BinaryState?.text()) {
 			log.trace "Got SetBinaryStateResponse = ${body?.Body?.SetBinaryStateResponse?.BinaryState?.text()}"
 		} else if (body?.property?.BinaryState?.text()) {
        	def value = body?.property?.BinaryState?.text().substring(0, 1).toInteger() == 0 ? "off" : "on"
        	log.trace "Notify: BinaryState = ${value}, ${body.property.BinaryState}"
            def dispaux = device.currentValue("switch") != value
            result << createEvent(name: "switch", value: value, descriptionText: "Switch is ${value}", displayed: dispaux)
        } else if (body?.Body?.GetInsightParamsResponse?.InsightParams?.text()) {
        	def params = body?.Body?.GetInsightParamsResponse?.InsightParams?.text().split("\\|")
        	def value = params[0].toInteger() == 0 ? "off" : "on"
            def energy = Math.ceil(params[8].toInteger() / 60000000)
            def power = Math.round(params[7].toInteger()/1000)
            def onToday = Math.floor(params[3].toInteger() / 60)
            log.trace "Status: $value"
            log.trace "Energy: $energy"
            log.trace "Power: $power"
            result << createEvent(name: "switch", value: value, descriptionText: "Switch is ${value}", displayed: (device.currentValue("switch") != value))
          //  result << createEvent(name: "energy", value: energy, descriptionText: "Energyh is ${energy} KWH", displayed: (device.currentValue("enrgy") != energy))
            result << createEvent(name: "power", value: power, descriptionText: "Power is ${power} W", displayed: (device.currentValue("power") != energy))
            result << createEvent(name: "onToday", value: onToday, descriptionText: "On today ${onToday} min", displayed: (device.currentValue("onToday") != onToday))
        } else if (body?.property?.TimeZoneNotification?.text()) {
 			log.debug "Notify: TimeZoneNotification = ${body?.property?.TimeZoneNotification?.text()}"
 		} else if (body?.Body?.GetBinaryStateResponse?.BinaryState?.text()) {
 			def value = body?.Body?.GetBinaryStateResponse?.BinaryState?.text().substring(0, 1).toInteger() == 0 ? "off" : "on"
 			log.trace "GetBinaryResponse: BinaryState = ${value}, ${body.property.BinaryState}"
            log.info "Connection: ${device.currentValue("connection")}"
            if (device.currentValue("currentIP") == "Offline") {
                def ipvalue = convertHexToIP(getDataValue("ip"))
                sendEvent(name: "IP", value: ipvalue, descriptionText: "IP is ${ipvalue}")
            }
            def dispaux2 = device.currentValue("switch") != value
     		result << createEvent(name: "switch", value: value, descriptionText: "Switch is ${value}", displayed: dispaux2)
 		}
 	}
 result
}

private getTime() {
    // This is essentially System.currentTimeMillis()/1000, but System is disallowed by the sandbox.
    ((new GregorianCalendar().time.time / 1000l).toInteger()).toString()
}

private getCallBackAddress() {
 	device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
}

private Integer convertHexToInt(hex) {
 	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
 	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private getHostAddress() {
 	def ip = getDataValue("ip")
 	def port = getDataValue("port")
 	if (!ip || !port) {
 		def parts = device.deviceNetworkId.split(":")
 		if (parts.length == 2) {
 			ip = parts[0]
 			port = parts[1]
 		} else {
 			log.warn "Can't figure out ip and port for device: ${device.id}"
		 }
 	}
 	log.debug "Using ip: ${ip} and port: ${port} for device: ${device.id}"
 	return convertHexToIP(ip) + ":" + convertHexToInt(port)
}

def on() {
log.debug "Executing 'on'"
def turnOn = new physicalgraph.device.HubAction("""POST /upnp/control/basicevent1 HTTP/1.1
SOAPAction: "urn:Belkin:service:basicevent:1#SetBinaryState"
Host: ${getHostAddress()}
Content-Type: text/xml
Content-Length: 333

<?xml version="1.0"?>
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" SOAP-ENV:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<SOAP-ENV:Body>
 <m:SetBinaryState xmlns:m="urn:Belkin:service:basicevent:1">
<BinaryState>1</BinaryState>
 </m:SetBinaryState>
</SOAP-ENV:Body>
</SOAP-ENV:Envelope>""", physicalgraph.device.Protocol.LAN)
}

def off() {
log.debug "Executing 'off'"
def turnOff = new physicalgraph.device.HubAction("""POST /upnp/control/basicevent1 HTTP/1.1
SOAPAction: "urn:Belkin:service:basicevent:1#SetBinaryState"
Host: ${getHostAddress()}
Content-Type: text/xml
Content-Length: 333

<?xml version="1.0"?>
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" SOAP-ENV:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<SOAP-ENV:Body>
 <m:SetBinaryState xmlns:m="urn:Belkin:service:basicevent:1">
<BinaryState>0</BinaryState>
 </m:SetBinaryState>
</SOAP-ENV:Body>
</SOAP-ENV:Envelope>""", physicalgraph.device.Protocol.LAN)
}

def subscribe(hostAddress) {
log.debug "Executing 'subscribe()'"
def address = getCallBackAddress()
new physicalgraph.device.HubAction("""SUBSCRIBE /upnp/event/basicevent1 HTTP/1.1
HOST: ${hostAddress}
CALLBACK: <http://${address}/>
NT: upnp:event
TIMEOUT: Second-${60 * (parent.interval?:5)}
User-Agent: CyberGarage-HTTP/1.0


""", physicalgraph.device.Protocol.LAN)
}

def subscribe() {
	subscribe(getHostAddress())
}

def refresh() {
    log.debug "Executing WeMo Switch 'subscribe',  then 'poll'"
    [subscribe(), poll()]
}

def subscribe(ip, port) {
    def existingIp = getDataValue("ip")
    def existingPort = getDataValue("port")
    if (ip && ip != existingIp) {
         log.debug "Updating ip from $existingIp to $ip"    
    	 updateDataValue("ip", ip)
    	 def ipvalue = convertHexToIP(getDataValue("ip"))
         sendEvent(name: "currentIP", value: ipvalue, descriptionText: "IP changed to ${ipvalue}")
    }
 	if (port && port != existingPort) {
 		log.debug "Updating port from $existingPort to $port"
 		updateDataValue("port", port)
	}
	subscribe("${ip}:${port}")
}

def resubscribe() {
    log.debug "Executing 'resubscribe()'"
    def sid = getDeviceDataByName("subscriptionId")
new physicalgraph.device.HubAction("""SUBSCRIBE /upnp/event/basicevent1 HTTP/1.1
HOST: ${getHostAddress()}
SID: uuid:${sid}
TIMEOUT: Second-300


""", physicalgraph.device.Protocol.LAN)
}


def unsubscribe() {
    def sid = getDeviceDataByName("subscriptionId")
new physicalgraph.device.HubAction("""UNSUBSCRIBE publisher path HTTP/1.1
HOST: ${getHostAddress()}
SID: uuid:${sid}


""", physicalgraph.device.Protocol.LAN)
}


//TODO: Use UTC Timezone
def timeSyncResponse() {
log.debug "Executing 'timeSyncResponse()'"
new physicalgraph.device.HubAction("""POST /upnp/control/timesync1 HTTP/1.1
Content-Type: text/xml; charset="utf-8"
SOAPACTION: "urn:Belkin:service:timesync:1#TimeSync"
Content-Length: 376
HOST: ${getHostAddress()}
User-Agent: CyberGarage-HTTP/1.0

<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
 <s:Body>
  <u:TimeSync xmlns:u="urn:Belkin:service:timesync:1">
   <UTC>${getTime()}</UTC>
   <TimeZone>-05.00</TimeZone>
   <dst>1</dst>
   <DstSupported>1</DstSupported>
  </u:TimeSync>
 </s:Body>
</s:Envelope>
""", physicalgraph.device.Protocol.LAN)
}

def setOffline() {
	sendEvent(name: "currentIP", value: "Offline", displayed: false)
    sendEvent(name: "switch", value: "offline", descriptionText: "The device is offline")
}
/*
def poll() {
log.debug "Executing 'poll'"
if (device.currentValue("currentIP") != "Offline")
    runIn(30, setOffline)
new physicalgraph.device.HubAction("""POST /upnp/control/basicevent1 HTTP/1.1
SOAPACTION: "urn:Belkin:service:basicevent:1#GetBinaryState"
Content-Length: 277
Content-Type: text/xml; charset="utf-8"
HOST: ${getHostAddress()}
User-Agent: CyberGarage-HTTP/1.0

<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body>
<u:GetBinaryState xmlns:u="urn:Belkin:service:basicevent:1">
</u:GetBinaryState>
</s:Body>
</s:Envelope>""", physicalgraph.device.Protocol.LAN)
}

*/

def poll() {
	log.debug "Executing 'poll'"
    if (device.currentValue("currentIP") != "Offline")
        runIn(30, setOffline)


	new physicalgraph.device.HubAction([
        'method': 'POST',
        'path': '/upnp/control/insight1',
        'body': """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:GetInsightParams xmlns:u="urn:Belkin:service:insight:1"></u:GetInsightParams>
                    </s:Body>
                </s:Envelope>
                """,
        'headers': [
            'HOST': getHostAddress(),
            'Content-type': 'text/xml; charset=utf-8',

            'SOAPAction': "\"urn:Belkin:service:insight:1#GetInsightParams\""
        ]
    ], device.deviceNetworkId)
    
    
}
