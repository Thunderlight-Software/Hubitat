import hubitat.device.*

metadata {
    definition (name: "Aqara 1 Button Wired Wall Switch", namespace: "Thunderlight", author: "G.Crean") {
        capability "Actuator"
        capability "Refresh"
        fingerprint profileId: "0104", inCluster: "0000, 0002, 0006, 0008", manufacturer:"LUMI", model: "lumi.ctrl_neutral1", deviceJoinName: "Aqara 1 Button Switch"
    }  
}

def parse(String description) {
   Map msgMap = null
   log.debug "Parse : $description"
   msgMap = zigbee.parseDescriptionAsMap(description)
   Map map = [:]

    if (description?.startsWith('read attr -')) {
        parseReadAttr(msgMap)
    }
    else if (description?.startsWith('catchall:')) {
        map = parseCatchAll(msgMap)
    }
}

def parseCatchAll(Map msgMap) {
    List<Map> values = []

    if (msgMap["clusterId"] == "0006" && msgMap["command"] == "0B"  ){
        if (msgMap["data"][0] == "01") {
            values.add([name:"switch",value:"on"])
            getChild("sw1").parse(values)
        }
        else { 
            values.add([name:"switch",value:"off"])
            getChild("sw1").parse(values)
        }
    }
}


def parseReadAttr(Map msgMap) {
    
    List<Map> values = []

    //Temperature Cluster
    if (msgMap["cluster"] == "0002" && msgMap["attrId"] == "0000") {
        getChild("temp").setTemperature(convertHexToInt(msgMap["value"]))
    }

    if (msgMap["cluster"] == "0008" && msgMap["attrId"] == "0000") {
        values.add([name:"switch",value:"off"])
        getChild("sw1").parse(values)
    }

    //Switch Cluster
    if (msgMap["cluster"] == "0006" && msgMap["attrId"] == "0000") {

        //Switch 1
        if (msgMap["endpoint"] == "02") {
            if (msgMap["value"] == "01") {
                values.add([name:"switch",value:"on",descriptionText:"Switch On"])
                getChild("sw1").parse(values)
            }else{
                values.add([name:"switch",value:"off",,descriptionText:"Switch Off"])
                getChild("sw1").parse(values)
            }
        }
    }
}

def refresh() {
    log.debug "refreshing ${device.deviceNetworkId}"
    sendZigbeeCommands( 
    [
        "he rattr 0x${device.deviceNetworkId} 0x02 0x0006 0", "delay 500",
        "he rattr 0x${device.deviceNetworkId} 0x02 0x0006 0", "delay 250",
        "he rattr 0x${device.deviceNetworkId} 0x01 0x0002 0", "delay 250",
        "he rattr 0x${device.deviceNetworkId} 0x01 0x0001 0", "delay 250",
        "he rattr 0x${device.deviceNetworkId} 0x01 0x0000 0"
    ])
}

def getChild(String type) {
    def child = getChildDevice("${device.deviceNetworkId}-${type}")
    if (!child) {
        switch (type) {
            case "sw1":
                child = addChildDevice("hubitat","Generic Component Switch","${device.deviceNetworkId}-sw1",[name:"${device.displayName} Switch", isComponent:true ])
                break

            case "temp":
                child = addChildDevice("hubitat","Virtual Temperature Sensor","${device.deviceNetworkId}-temp",[name:"${device.displayName} Temperature", isComponent:true ])
                break
        }
    }
    return child
}

private Integer convertHexToInt(hex) {
    Integer.parseInt(hex,16)
}

void sendZigbeeCommand(String cmd) {
    sendZigbeeCommands([cmd])
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    HubMultiAction allActions = new HubMultiAction()
    cmd.each {
            allActions.add(new HubAction(it, Protocol.ZIGBEE))
    }
    sendHubCommand(allActions)
}

//Child Events
def componentRefresh(child) {
    refresh()
}

def componentOn(child) {
    sendZigbeeCommand("he cmd 0x${device.deviceNetworkId} 0x02 0x0006 1 {}")
}

def componentOff(child) {
    sendZigbeeCommand("he cmd 0x${device.deviceNetworkId} 0x02 0x0006 0 {}")
}