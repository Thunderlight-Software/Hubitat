// https://docs.hubitat.com/index.php?title=App_Object
// https://docs.hubitat.com/index.php?title=MQTT_Interface
// https://docs.hubitat.com/index.php?title=Common_Methods_Object
// https://docs.hubitat.com/index.php?title=Device_Object
// https://docs.hubitat.com/index.php?title=Driver_Capability_List
// https://homieiot.github.io/specification
// http://www.groovy-lang.org/gdk.html


metadata {
    definition (name: "MQTT Bridge Driver", namespace: "Thunderlight", author: "Gary Crean",importUrl: "") {
        capability "Initialize"
		capability "PresenceSensor"
        command "connect"
        command "disconnect"
        command "publishMsg", ["String","String"]
        command "setWill", ["String","String"]
		command "subscribeTopic",["String"]
        command "unsubscribeTopic",["String"]
        command "clear"
    }
}

/***************************************************************************************************/
// Driver Entry Points
def installed() {
    log.info("Installed")
	initialize()
}

def updated() {
    log.info("Updated")
    initialize()    
}

def uninstalled() {
    log.info("Uninstalled")
    clear()
    interfaces.mqtt.disconnect()
}

def initialize() {
    sendEvent (name: "presence", value: "not present")
    state.id = UUID.randomUUID().toString()
    Set<String> retained = []
    state.retained = retained
}

/***************************************************************************************************/
// MQTT Callbacks
def parse(String message) {
    topic = interfaces.mqtt.parseMessage(message).topic
    payload = interfaces.mqtt.parseMessage(message).payload
    runInMillis(5,"message", [data: [topic: topic, payload:payload]])
}

def mqttClientStatus(String message) {
    log.info(message)
    runInMillis(5,"connected")
//  parent.mqttDisconnected()
//  parent.mqttError()  
}

// Runin Methods
def connected() {
    sendEvent (name: "presence", value: "present")
    parent.mqttConnected()
}

def disconnected() {
    sendEvent (name: "presence", value: "not present")
    parent.mqttDisconnected()
}

def message(Map data) {
    parent.mqttMessage(data.topic,data.payload)
}

/***************************************************************************************************/
// Commands
def connect() {
    log.info("Connecting as ${state.id} to MQTT broker ${state.broker}")
    interfaces.mqtt.connect(state.broker,state.id,state.username,state.password,lastWillTopic:state.willTopic,lastWillQos:0,lastWillMessage:state.payload)
}

def disconnect() {
    log.info("Disconnecting")
    interfaces.mqtt.disconnect()
}

def publishMsg(String topic, String payload, int qos, String retained) {
    publishMsg(topic, payload, qos, retained.toBoolean())
}

def publishMsg(String topic, String payload,int qos = 1, boolean retained = false ) {
    retainedList = state.retained
    if (retained && !retainedList.contains(topic)) {
        retainedList.add(topic)
        state.retained = retainedList
    }
    interfaces.mqtt.publish(topic, payload, qos, retained)
}

def setWill(String topic,String payload) {
    state.willTopic = topic
    state.willPayload = payload
}

def setBroker(String broker) {
    state.broker = broker
}

def setUsername(String username) {
    state.username = username
}

def setPassword(String password) {
    state.password = password
}

def subscribeTopic (String topic) {
    interfaces.mqtt.subscribe(topic,1)
}

def unsubscribeTopic (String topic) {
    interfaces.mqtt.unsubscribe(topic)
}

def clear() {
    retained = state.retained
    retained.each { topic -> 
      interfaces.mqtt.publish(topic,"",0,false)
    }
    retained.clear()
    state.retained = retained
}