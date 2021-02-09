definition(
	name: "Home Assistant Bridge",
	namespace: "Thunderlight",
	author: "Gary Crean",
	importUrl: "https://raw.githubusercontent.com/xAPPO/MQTT/beta3/MQTT%20app",
	description: "Links MQTT with Hubitat devices",
	category: "Intranet Connectivity",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-BigButtonsAndSwitches.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-BigButtonsAndSwitches@2x.png",
    installOnOpen: true,
	iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-BigButtonsAndSwitches@2x.png"
)

preferences {  
            page(name: "configuration", title: "<h2><b>MQTT</b></h2>",install: true, uninstall: true, hideable: true,hideWhenEmpty: true){
            section ("<b>MQTT Broker</b>", hideable: true, hidden: true){
				input name: "hubName",  type: "text", title: "<b>Hub Name</b>", description: "  choose a unique name for this Hubitat Hub", required: true, displayDuringSetup: true, submitOnChange: false
		        input name: "MQTTBroker", type: "text", title: "<b>MQTT Broker Address</b>&nbsp&nbsp&nbsp&nbsp&nbsp prefixed tcp://...", description: "e.g. tcp://192.168.1.17:1883  - NB you must include tcp://...", required: true, displayDuringSetup: true
		        input name: "username", type: "text", title: "<b>MQTT Username</b>", description: "(leave blank if none)", required: false, displayDuringSetup: true
		        input name: "password", type: "password", title: "<b>MQTT Password</b>", description: "(leave blank if none)", required: false, displayDuringSetup: true
            }
                
			section ("<b>Publish these HE devices to MQTT</b>",hideWhenEmpty: true, hideable: true, hidden: true) {
                input "modes", "bool", title: "Mode changes", required: false
                input "hsm", "bool", title: "Hubitat Security Monitor", required: false
				input "lock", "bool", title: "Allow unlock", required: false
                input "everything", "capability.*",hideWhenEmpty: true, multiple: true, required: false, title: "<b>Everything (all capabilities/attributes a selected device supports)</b>", submitOnChange: false
			}	
		}
}

import groovy.json.JsonOutput

/***************************************************************************************************/
// App Entry Points
def installed() {
    log.info("Installed")
	initialize()
}

/************************/
def updated() {
    log.info("Updated")
	mqtt = getChildDevice("MQTT: HA Bridge Driver")
    
    //Setup MQTT Driver
    mqtt.clear()
	mqtt.disconnect()
	mqtt.setUsername (settings?.username)
    mqtt.setPassword (settings?.password)
	mqtt.setBroker(settings?.MQTTBroker)
	mqtt.setWill("homie/" + (settings?.hubName) + '/$state',"lost")
	mqtt.connect()
    syncDevices()    
}

/************************/
def uninstalled() {
    log.info("Uninstalled")
	mqtt = getChildDevice("MQTT: HA Bridge Driver")
	mqtt.clear()
	mqtt.disconnect()
	deleteChildDevice("MQTT: HA Bridge Driver")
}

/************************/
def initialize() {
	log.info("Initialize")
	childDev=getChildDevice("MQTT: HA Bridge Driver")  
 	if (childDev == null) {
		addChildDevice("Thunderlight", "MQTT Bridge Driver", "MQTT: HA Bridge Driver", null,[completedSetup: true,name: "MQTT: HA Bridge Driver", logging: false])
	}
	mqtt = getChildDevice("MQTT: HA Bridge Driver")
	atomicState.mqttSettable = [:]

	//Setup MQTT Driver
	mqtt.setUsername (settings?.username)
    mqtt.setPassword (settings?.password)
	mqtt.setBroker(settings?.MQTTBroker)
	mqtt.setWill("homie/" + (settings?.hubName) + '/$state',"lost")
	mqtt.connect()
}

/***************************************************************************************************/
// MQTT Callbacks
def mqttMessage(String topic,String payload) {
	mqttTopics = atomicState.mqttSettable
	dev=mqttTopics[topic]?.device
	handler=mqttTopics[topic]?.handler
	if(handler != null && dev != null) runInMillis(5,handler, [data: [device:dev, payload:payload]])
}

/************************/
def mqttConnected() {
	log.info("MQTT Broker connected")
	atomicState.connected = true
	mqtt = getChildDevice("MQTT: HA Bridge Driver")
	mqtt.publishMsg("homie/" + (settings?.hubName),"",1,true)
	mqtt.publishMsg("homie/" + (settings?.hubName) + '/$homie',"3.0.1",1,true)
	mqtt.publishMsg("homie/" + (settings?.hubName) + '/$name',"$mqtt.hub.name",1,true)
	mqtt.publishMsg("homie/" + (settings?.hubName) + '/$state',"init",1,true)
	mqtt.publishMsg("homie/" + (settings?.hubName) + '/$localip',"$mqtt.hub.data.localIP",1,true)
	mqtt.publishMsg("homie/" + (settings?.hubName) + '/$extensions',"",1,true)
	mqtt.subscribeTopic("homie/" + (settings?.hubName) + "/+/+/set")
	mqtt.subscribeTopic("homie/" + (settings?.hubName) + "/+/+/+/set")
	syncDevices()
	mqtt.publishMsg("homie/" + (settings?.hubName) + '/$state',"ready",1,true)
}

/************************/
def mqttDisconnected() {
	log.info("MQTT Broker disconnected")
	atomicState.connected = false

	//TODO: Retry connection
}
/************************/
def mqttError() {

}
/***************************************************************************************************/
private def syncDevices() {
	devices = (settings?.everything)
	firstNode = true
	nodes=""
	devices.each { dev ->
		node = publishDevice(dev)
		if (node != null) 
		{
			if (!firstNode) nodes += ","
			firstNode = false
			nodes += node
		}
	}
	mqtt.publishMsg("homie/" + (settings?.hubName) + '/$nodes',nodes,1,true)
}

/************************/
// Publish device Capabilities
// https://docs.hubitat.com/index.php?title=Driver_Capability_List
private def publishDevice(dev) {

	if (dev.displayName.startsWith("MQTT")) return null
	normName = normalize(dev.displayName)
	nodeTopic = "homie/" + (settings?.hubName) + "/$normName"
	Set<String> properties = []
	firstProp = true
	dev.capabilities.each { cap ->
		property = null
		switch (cap.name)
		{
			case "AccelerationSensor":
				property = advertiseAttribute(dev,"acceleration","enum","[inactive,active]")
				if (property != null) advertiseHABinarySensor(dev,"acceleration","vibration","active","inactive")
				break

			case "Alarm":
				property = advertiseAttribute(dev,"alarm","enum","[strobe,off,both,siren]")
				break

			case "Battery":
				property = advertiseAttribute(dev,"battery","integer","0:100",'%')
				if (property != null) advertiseHASensor(dev,"battery","battery","%")
				break

			case "Beacon":
				property = advertiseAttribute(dev,"beacon","enum","[not present,present]")
				if (property != null) advertiseHABinarySensor(dev,"beacon",null,"present","not present")
				break

			case "ColorControl":
			case "ColorMode":
				if (dev.hasAttribute("RGB")) subscribe(dev,"RGB",HubColorEvent)
				if (dev.hasAttribute("color")) subscribe(dev,"color",HubColorEvent)
				if (dev.hasAttribute("colorName")) subscribe(dev,"colorName",HubColorEvent)
				if (dev.hasAttribute("hue")) subscribe(dev,"hue",HubColorEvent)
				if (dev.hasAttribute("saturation")) subscribe(dev,"saturation",HubColorEvent)
				if (dev.hasAttribute("level")) subscribe(dev,"level",HubColorEvent)
				property = advertiseProperty(dev,"color","hs",null,null,onColorMsg)
				PublishColor(dev)
				break

			case "ColorTemperature":
				property = advertiseAttribute(dev,"colorTemperature","ct",null,null,onColorTempMsg)
				break

			case "ContactSensor":
				property = advertiseAttribute(dev,"contact","enum","[closed,open]")
				if (property != null) advertiseHABinarySensor(dev,"contact","opening","open","closed")
				break	

			case "EnergyMeter":
				property = advertiseAttribute(dev,"energy","float")
				if (property != null) advertiseHASensor(dev,"energy","energy")
				break

			case "Lock":
				property = advertiseAttribute(dev,"lock","enum","[locked,unlocked with timeout,unlocked,unknown]",null,onLockMsg)
				if (property != null) advertiseHALock(dev)
				break

			case "MotionSensor":
				property = advertiseAttribute(dev,"motion","enum","[active,inactive]")
				if (property != null) advertiseHABinarySensor(dev,"motion","motion","active","inactive")
				break

			case "PowerMeter":
				property = advertiseAttribute(dev,"power","float")
				if (property != null) advertiseHASensor(dev,"power","power")
				break

			case "Bulb":
			case "Light":
			case "RelaySwitch":
			case "Outlet":
			case "Switch":
				property = advertiseAttribute(dev,"switch","enum","[on,off]",null,onSwitchMsg)
				if (property != null && ( dev.hasCapability("Light") || dev.hasCapability("Bulb"))) {
					advertiseHALight(dev)
				}else {
					advertiseHASwitch(dev)
				}
				break

			case "SwitchLevel":
				property = advertiseAttribute(dev,"level","integer","0:100",null,onLevelChange)
				break	

			case "PowerSource":
				property = advertiseAttribute(dev,"powerSource","enum","[battery,dc,mains,unknown]")
				break	

			case "PresenceSensor":
				property = advertiseAttribute(dev,"presence","enum","[present,not present]")
				if (property != null) advertiseHABinarySensor(dev,"presence","presence","present","not present")
				break

			case "PressureMeasurement":
				property = advertiseAttribute(dev,"pressure","integer")
				break

			case "PushableButton":
				buttonCount = dev.currentValue("numberOfButtons")
				for (int count=1; count <= buttonCount; count++) {
					property = advertiseProperty(dev,"pushed_$count","boolean")
					properties.add(property)
					subscribe(dev,"pushed",HubButtonEvent)
					
					//Holdable Button
					if (dev.hasCapability("HoldableButton")) {
						property = advertiseProperty(dev,"held_$count","boolean")
						properties.add(property)
						subscribe(dev,"held",HubButtonEvent)
					}

					//Release Button
					if (dev.hasCapability("ReleasableButton")) {
						property = advertiseProperty(dev,"released_$count","boolean")
						properties.add(property)
						subscribe(dev,"released",HubButtonEvent)
					}


					advertiseHATrigger(dev,count)
				}
				break

			case "RelativeHumidityMeasurement":
				property = advertiseAttribute(dev,"humidity","float","0:100",'%')
				if (property != null) advertiseHASensor(dev,"humidity","humidity","%","{{ value | round(0) }}")
				break

			case "TemperatureMeasurement":
				property = advertiseAttribute(dev,"temperature","float","-490:9999",'°C')
				if (property != null) advertiseHASensor(dev,"temperature","temperature",'°C',"{{ value | round(0) }}")
				break

			//Ignore these...
			case "HoldableButton":
			case "ReleasableButton":
			case "Initialize":
			case "Sensor":
			case "Configuration":
			case "Polling":
			case "Actuator":
			case "Refresh":
			case "ChangeLevel":
				break

			default:
				log.info("Device :$dev.displayName -> $cap.name ( Unknown )")
				break	
		}

		//Add to Properties list
		if (property != null) properties.add(property)
	}

	//Advise if Capability available
	if (!properties.isEmpty()) {
		mqtt.publishMsg(nodeTopic + '/$name',"$dev.displayName",1,true)
		mqtt.publishMsg(nodeTopic + '/$type',"$dev.displayName",1,true)					//TODO: Define Type of node
		props=""
		propFirst = true
		properties.each { item -> 
			if (!propFirst) props += ","
			propFirst = false
			props += item
		}
		mqtt.publishMsg(nodeTopic + '/$properties',"$props",1,true)
		return normName
	}
	return null
}

/***************************************************************************************************/
//Advertise a attribute to Homie scheme
/***************************************************************************************************/
private def advertiseAttribute(dev,attribute,datatype,format = null,unit = null,handler = null) {
	normName = normalize(dev.displayName)
	topic = "homie/" + (settings?.hubName) + "/$normName" + "/$attribute"
	if (!dev.hasAttribute(attribute)) return null
	mqtt.publishMsg(topic,dev.currentValue(attribute).toString(),1,true)
	subscribe(dev,attribute,HubEvent)
	return advertiseProperty(dev,attribute,datatype,format,unit,handler)
}

/************************/
private def advertiseProperty(dev,attribute,datatype,format = null,unit = null,handler = null) {
	normName = normalize(dev.displayName)
	topic = "homie/" + (settings?.hubName) + "/$normName" + "/$attribute"

	//Add type info
	mqtt.publishMsg(topic + "/" + '$name',"$dev.displayName",1,true)
	mqtt.publishMsg(topic + "/" + '$datatype',"$datatype",1,true)
	if (format != null) mqtt.publishMsg(topic + "/" + '$format',"$format",1,true)
	if (unit != null) mqtt.publishMsg(topic + "/" + '$unit',"$unit",1,true)

	//Settable Property
	if (handler != null) {
		mqtt.publishMsg(topic + "/" + '$settable',"true",1,true)
		mqttTopics = atomicState.mqttSettable
		mqttTopics[topic+"/set"] = [device: dev.getIdAsLong(), handler: handler]
		atomicState.mqttSettable = mqttTopics
	}
	return attribute
}

/***************************************************************************************************/
// Advertise HA Autodiscovery
/***************************************************************************************************/
//https://www.home-assistant.io/integrations/binary_sensor.mqtt
private def advertiseHABinarySensor(dev,property,devclass=null,on=null,off=null,template=null) {
	normName = normalize(dev.displayName)
	hatopic = "homeassistant/binary_sensor/$normName/$property/config"
	topic = "homie/" + (settings?.hubName) + "/$normName" + "/$property"
	config = createHADiscoveryBase(dev,property)
	config["state_topic"] = topic
	config["payload_on"] = on
	config["payload_off"] = off
	if (devclass != null) config["device_class"] = devclass
	if (template != null) config["value_template"] = template
	mqtt.publishMsg(hatopic,JsonOutput.toJson(config),1,true)
}
/************************/
//https://www.home-assistant.io/integrations/sensor.mqtt
private def advertiseHASensor(dev,property,devclass=null,uom=null,template=null) {
	normName = normalize(dev.displayName)
	hatopic = "homeassistant/sensor/$normName/$property/config"
	topic = "homie/" + (settings?.hubName) + "/$normName" + "/$property"
	config = createHADiscoveryBase(dev,property)
	config["state_topic"] = topic
	if (uom != null) config["unit_of_measurement"] = uom
	if (devclass != null) config["device_class"] = devclass
	if (template != null) config["value_template"] = template
	mqtt.publishMsg(hatopic,JsonOutput.toJson(config),1,true)
}

/************************/
//https://www.home-assistant.io/integrations/switch.mqtt
private def advertiseHASwitch(dev) {
	normName = normalize(dev.displayName)
	hatopic = "homeassistant/switch/$normName/onoff/config"
	topic = "homie/" + (settings?.hubName) + "/$normName" + "/$property"
	config = createHADiscoveryBase(dev,"")
	config["state_topic"] = topic
	config["command_topic"] = topic + "/set"
	config["payload_off"] = config["state_off"] = "off"
	config["payload_on"] = config["state_on"] = "on"
	mqtt.publishMsg(hatopic,JsonOutput.toJson(config),1,true)
}
/************************/
//https://www.home-assistant.io/integrations/light.mqtt
private def advertiseHALight(dev) {
	normName = normalize(dev.displayName)
	hatopic = "homeassistant/light/$normName/config"
	topic = "homie/" + (settings?.hubName) + "/$normName"
	config = createHADiscoveryBase(dev,"")
	config["state_topic"] = topic + "/switch"
	config["command_topic"] = topic + "/switch/set"
	config["payload_off"] = "off"
	config["payload_on"] = "on"

	if (dev.hasCapability("SwitchLevel")) {
		config["brightness_state_topic"] = topic + "/level"
		config["brightness_command_topic"] = topic + "/level/set"
		config["brightness_scale"] = 100
	}

	if (dev.hasCapability("ColorControl")) {
		config["hs_state_topic"] = topic + "/color"
		config["hs_command_topic"] = topic + "/color/set"	
	}

	if (dev.hasCapability("ColorTemperature")) {
		config["color_temp_state_topic"] = topic + "/colorTemperature"
		config["color_temp_command_topic"] = topic + "/colorTemperature/set"	
	}

	mqtt.publishMsg(hatopic,JsonOutput.toJson(config),1,true)
}

/************************/
//https://www.home-assistant.io/integrations/lock.mqtt
private def advertiseHALock(dev) {
	normName = normalize(dev.displayName)
	hatopic = "homeassistant/lock/$normName/config"
	topic = "homie/" + (settings?.hubName) + "/$normName"
	config = createHADiscoveryBase(dev,"")
	config["state_topic"] = topic + "/lock"
	config["command_topic"] = topic + "/lock/set"
	config["payload_lock"] = "lock"
	config["payload_unlock"] = "unlock"
	config["state_locked"] = "locked"
	config["state_unlocked"] = "unlocked"
	mqtt.publishMsg(hatopic,JsonOutput.toJson(config),1,true)
}

/************************/
//https://www.home-assistant.io/integrations/device_trigger.mqtt
private def advertiseHATrigger(dev,button) {
	normName = normalize(dev.displayName)
	hatopic = "homeassistant/device_automation/$normName" + "-pushed_$button/config"
	topic = "homie/" + (settings?.hubName) + "/$normName"
	c = createHADiscoveryBase(dev,"")
	config = [:]
	config["device"] = c["device"]
	config["automation_type"]="trigger"
	config["topic"] = topic + "/pushed_$button"
	config["type"] = "button_short_press"
	config["subtype"] = "button_$button"
	mqtt.publishMsg(hatopic,JsonOutput.toJson(config),1,true)

	//Held Button
	if (dev.hasCapability("HoldableButton")) {
		hatopic = "homeassistant/device_automation/$normName" + "-held_$button/config"
		config["type"] = "button_long_press"
		config["topic"] = topic + "/held_$button"
		mqtt.publishMsg(hatopic,JsonOutput.toJson(config),1,true)
	}

	//Release Button
	if (dev.hasCapability("ReleasableButton")) {
		hatopic = "homeassistant/device_automation/$normName" + "-hrelease_$button/config"
		config["type"] = "button_long_release"
		config["topic"] = topic + "/released_$button"
		mqtt.publishMsg(hatopic,JsonOutput.toJson(config),1,true)
	}
} 

/************************/
//https://www.home-assistant.io/docs/mqtt/discovery/
private def createHADiscoveryBase(dev,property) {
	config = [:]
	device = [:]
	normName = normalize(dev.displayName)
	config["name"] = "$dev.displayName $property"
	config["unique_id"] = normName+"_" + property
	config["availability_topic"] = "homie/" + (settings?.hubName) + '/$state'
	config["payload_available"] = "ready"
	config["payload_not_available"] = "lost"
	device["name"] = dev.displayName
	device["identifiers"] = "Hubitat_"+dev.getIdAsLong()
	if (dev.getDataValue("manufacturer") != null ) device["manufacturer"] = dev.getDataValue("manufacturer")
	if (dev.getDataValue("model") != null )device["model"] = dev.getDataValue("model")
	config["device"] = device
	return config	
}

/***************************************************************************************************/
// MQTT Handlers to update device state
/***************************************************************************************************/
def onSwitchMsg(Map data) {
	device=getSubscribedDeviceById(data.device)
	if (data.payload.equals("on")) {
		device.on()
	}else if (data.payload.equals("off")) {
		device.off()
	}
}
/************************/
def onLevelChange(Map data) {
	device=getSubscribedDeviceById(data.device)
	level = data.payload.toInteger()
	device.setLevel(level,0)
	if (device.hasCapability("ColorControl")) PublishColor(device)
}
/************************/
def onColorMsg(Map data) {
	device=getSubscribedDeviceById(data.device)
	hs = data.payload.split(",")
	hue = Float.parseFloat(hs[0])
	sat = Float.parseFloat(hs[1]) + 0.5
	hue = (hue / 3.6) + 0.5
	device.setHue(hue)
	device.setSaturation(sat)
}
/************************/
def onColorTempMsg(Map data) {
	device=getSubscribedDeviceById(data.device)
	level = data.payload.toInteger()
	device.setColorTemperature(level)	
}

/************************/
def onLockMsg(Map data) {
	device=getSubscribedDeviceById(data.device)
	if ((settings?.lock) != true) {
		log.error("Unlock disabled by settings : $device.displayName")
		return
	}
	if (data.payload.equals("lock")) {
		device.lock()
	}else if (data.payload.equals("unlock")) {
		device.unlock()
	}
}

/***************************************************************************************************/
// Event Handlers to update MQTT
/***************************************************************************************************/
def HubEvent(evt) {
	mqtt = getChildDevice("MQTT: HA Bridge Driver")
	device=getSubscribedDeviceById(evt.getDeviceId())
	normName = normalize(device.displayName)
	topic = "homie/" + (settings?.hubName) + "/$normName" + "/$evt.name"
	mqtt.publishMsg(topic,"$evt.value",1,true)
}

/************************/
def HubColorEvent(evt) {
	device=getSubscribedDeviceById(evt.getDeviceId())
	log.info("$evt.name -> $evt.value")
	return PublishColor(device)
}

/************************/
def HubButtonEvent(evt) {
	mqtt = getChildDevice("MQTT: HA Bridge Driver")
	device=getSubscribedDeviceById(evt.getDeviceId())
	normName = normalize(device.displayName)
	topic = "homie/" + (settings?.hubName) + "/$normName/$evt.name" + "_$evt.value"
	mqtt.publishMsg(topic,"$evt.name",0,false)
}

/************************/
private def PublishColor(device) {
	mqtt = getChildDevice("MQTT: HA Bridge Driver")
	normName = normalize(device.displayName)
	topic = "homie/" + (settings?.hubName) + "/$normName" + "/color"
	hue = device.currentValue("hue")
	hue = (hue*3.6).toInteger()
	saturation = device.currentValue("saturation")
	mqtt.publishMsg(topic,"$hue,$saturation",1,true)
}

/***************************************************************************************************/
// Helpers
/***************************************************************************************************/
private def normalize(name) {
	return name ? name.trim().toLowerCase().replaceAll(/[^\w-]/,"-").replaceAll(/[_]/,'-') : undefined
}
