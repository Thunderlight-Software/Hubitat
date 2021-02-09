metadata {
    definition (name: "Magic Home LightControl", namespace: "Thunderlight", author: "G.Crean") {
        capability "Initialize"
        capability "Refresh"
        capability "Light"
        capability "Switch"
        capability "ColorControl"
        capability "SwitchLevel"
        command "setIPAddress", ["String"]
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
}

def initialize() {
    interfaces.rawSocket.connect(state.ipAddress, 5577,byteInterface:true)
}

def setIPAddress(String ipAddress) {
    state.ipAddress = ipAddress
}

// https://github.com/vikstrous/zengge-lightcontrol/blob/master/README.md
def parse(String msg) {
    def resp = hubitat.helper.HexUtils.hexStringToByteArray(msg) 
    if (resp[0] != (byte) 0x081) return;
    def red = resp[6] & 0xff
    def green = resp[7] & 0xff
    def blue = resp[8] & 0xff

    hsv = hubitat.helper.ColorUtils.rgbToHSV([red,green,blue])
    state.hue = hsv[0]
    state.saturation = hsv[1]
    state.level = hsv[2]
    sendEvent(name: "switch", value: resp[2] == 0x23 ? "on" : "off")
    sendEvent(name: "colorMode", value:"RGB")
    sendEvent(name: "saturation",value: state.saturation)
    sendEvent(name: "hue",value: state.hue)
    sendEvent(name: "level",value: state.level)
}

def refresh() {
    initialize()
    def Cmd = [ (byte) 0x81, (byte) 0x8a, (byte) 0x8b ] as byte[];
    sendMsg(Cmd);
    runIn(1, 'refresh')
}

def on() {
   def Cmd = [ 0x71, 0x23, 0x0f ] as byte[];
   sendMsg(Cmd);
}

def off() {
   def Cmd = [ 0x71, 0x24, 0x0f ] as byte[];
   sendMsg(Cmd);    
}

def setColor(color) {
    sendColor([color["hue"],color["saturation"],color["level"]])
}

def setHue(hue) {
    sendColor([hue, state.saturation, state.level])
}

def setSaturation(saturation) {
    sendColor([state.hue, saturation, state.level])
}

def setLevel(level,duration) {
    sendColor([state.hue, state.saturation, level])
}

def socketStatus(String msg) {
    log.info(msg)
}

def sendColor(hsv) {
    def rgb = hubitat.helper.ColorUtils.hsvToRGB(hsv)
    Cmd = [ 0x31, (byte)(rgb[0] & 0xff),(byte)(rgb[1] & 0xff),(byte)(rgb[2] & 0xff), 0x00, (byte) 0x0f, 0x0f ] as byte[];
    state.hue = hsv[0]
    state.saturation = hsv[1]
    state.level = hsv[2]
    sendMsg(Cmd)
}

def sendMsg(byte[] command) {
    byte[] outBuf = new byte[command.length + 1]
    long crc = 0;
    for (int cnt=0; cnt < command.length; cnt++) {
        outBuf[cnt] = command[cnt]
        crc += outBuf[cnt]
    }
    outBuf[command.length] = (byte) (crc & 0xff)
    interfaces.rawSocket.sendMessage(hubitat.helper.HexUtils.byteArrayToHexString(outBuf))
}
