/*
*	Zen21 Central Scene Switch



Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.




*	version: 1.1
*/

import groovy.transform.Field

metadata {
    definition (name: "Zooz Zen21 Central Scene Switch", namespace: "tmastersmart", author: "Bryan Copeland", importUrl: "https://raw.githubusercontent.com/tmastersmart/hubitat-zooz-switch/master/Drivers/zooz/zen21-switch.groovy") {
//    definition (name: "Zooz Zen21 Central Scene Switch", namespace: "djdizzyd", author: "Bryan Copeland", importUrl: "https://raw.githubusercontent.com/djdizzyd/hubitat/master/Drivers/zooz/zen21-switch.groovy") {
        capability "Switch"
        capability "Refresh"
        capability "Actuator"
        capability "Sensor"
        capability "Configuration"
        capability "PushableButton"
        capability "Indicator"

        fingerprint mfr:"027A", prod:"B111", deviceId:"1E1C", inClusters:"0x5E,0x25,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x70,0x5B,0x6C,0x9F,0x7A", deviceJoinName: "Zooz Zen21 Switch" //US

    }
    preferences {
        configParams.each { input it.value.input }
        input name: "associationsG2", type: "string", description: "To add nodes to associations use the Hexidecimal nodeID from the z-wave device list separated by commas into the space below", title: "Associations Group 2"
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable text logging", defaultValue: false
    }
}
@Field static Map configParams = [
        1: [input: [name: "configParam1", type: "enum", title: "On/Off Paddle Orientation", description: "", defaultValue: 0, options: [0:"Normal",1:"Reverse",2:"Any paddle turns on/off"]], parameterSize: 1],
        2: [input: [name: "configParam2", type: "enum", title: "LED Indicator Control", description: "", defaultValue: 0, options: [0:"Indicator is on when switch is off",1:"Indicator is on when switch is on",2:"Indicator is always off",3:"Indicator is always on"]], parameterSize: 1],
        3: [input: [name: "configParam3", type: "enum", title: "Auto Turn-Off Timer", description: "", defaultValue: 0, options: [0:"Timer disabled",1:"Timer Enabled"]], parameterSize: 1],
        4: [input: [name: "configParam4", type: "number", title: "Auto Off Timer", description: "Minutes 1-65535", defaultValue: 60, range:"1..65535"], parameterSize:4],
        5: [input: [name: "configParam5", type: "enum", title: "Auto Turn-On Timer", description: "", defaultValue: 0, options: [0:"timer disabled",1:"timer enabled"]],parameterSize:1],
        6: [input: [name: "configParam6", type: "number", title: "Auto On Timer", description: "Minutes 1-65535", defaultValue: 60, range:"1..65535"], parameterSize: 4],
        7: [input: [name: "configParam7", type: "enum", title: "Association Reports", description: "", defaultValue: 15, options:[0:"none",1:"physical tap on ZEN26 only",2:"physical tap on 3-way switch only",3:"physical tap on ZEN26 or 3-way switch",4:"Z-Wave command from hub",5:"physical tap on ZEN26 or Z-Wave command",6:"physical tap on connected 3-way switch or Z-wave command",7:"physical tap on ZEN26 / 3-way switch / or Z-wave command",8:"timer only",9:"physical tap on ZEN26 or timer",10:"physical tap on 3-way switch or timer",11:"physical tap on ZEN26 / 3-way switch or timer",12:"Z-wave command from hub or timer",13:"physical tap on ZEN26, Z-wave command, or timer",14:"physical tap on ZEN26 / 3-way switch / Z-wave command, or timer", 15:"all of the above"]],parameterSize:1],
        8: [input: [name: "configParam8", type: "enum", title: "On/Off Status After Power Failure", description: "", defaultValue: 2, options:[0:"Off",1:"On",2:"Last State"]],parameterSize:1],
        9: [input: [name: "configParam9", type: "enum", title: "Enable/Disable Scene Control", defaultValue: 0, options:[0:"Scene control disabled",1:"scene control enabled"]],parameterSize:1],
        11: [input: [name: "configParam11", type: "enum", title: "Smart Bulb Mode", defaultValue: 1, options:[0:"physical paddle control disabled",1:"physical paddle control enabled",2:"physical paddle and z-wave control disabled"]],parameterSize: 1],
        12: [input: [name: "configParam12", type: "enum", title: "3-Way Switch Type", defaultValue: 0, options:[0:"Normal",1:"Momentary"]],parameterSize:1],
        13: [input: [name: "configParam13", type: "enum", title: "Report Type Disabled Physical", defaultValue:0, options: [0:"switch reports on/off status and changes LED indicator state even if physical and Z-Wave control is disabled", 1:"switch doesn't report on/off status or change LED indicator state when physical (and Z-Wave) control is disabled"]], parameterSize:1],
]
@Field static Map CMD_CLASS_VERS=[0x5B:3,0x86:3,0x72:2,0x8E:3,0x85:2,0x59:1,0x70:1]
@Field static int numberOfAssocGroups=2

void logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

void configure() {
    if (!state.initialized) initializeVars()
    runIn(5,pollDeviceData)
}

void initializeVars() {
    // first run only
    state.initialized=true
    runIn(5, refresh)
}

void updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    unschedule()
    if (logEnable) runIn(1800,logsOff)
    List<hubitat.zwave.Command> cmds=[]
    cmds.addAll(processAssociations())
    cmds.addAll(runConfigs())
    sendToDevice(cmds)
}

List<hubitat.zwave.Command> runConfigs() {
    List<hubitat.zwave.Command> cmds=[]
    configParams.each { param, data ->
        if (settings[data.input.name]) {
            cmds.addAll(configCmd(param, data.parameterSize, settings[data.input.name]))
        }
    }
    return cmds
}

List<hubitat.zwave.Command> pollConfigs() {
    List<hubitat.zwave.Command> cmds=[]
    configParams.each { param, data ->
        if (settings[data.input.name]) {
            cmds.add(zwave.configurationV1.configurationGet(parameterNumber: param.toInteger()))
        }
    }
    return cmds
}

List<hubitat.zwave.Command> configCmd(parameterNumber, size, scaledConfigurationValue) {
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: parameterNumber.toInteger(), size: size.toInteger(), scaledConfigurationValue: scaledConfigurationValue.toInteger()))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: parameterNumber.toInteger()))
    return cmds
}

void indicatorNever() {
    sendToDevice(configCmd(2,1,2))
}

void indicatorWhenOff() {
    sendToDevice(configCmd(2,1,0))
}

void indicatorWhenOn() {
    sendToDevice(configCmd(2,1,1))
}

void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    if(configParams[cmd.parameterNumber.toInteger()]) {
        Map configParam=configParams[cmd.parameterNumber.toInteger()]
        int scaledValue
        cmd.configurationValue.reverse().eachWithIndex { v, index -> scaledValue=scaledValue | v << (8*index) }
        device.updateSetting(configParam.input.name, [value: "${scaledValue}", type: configParam.input.type])
    }
}

void pollDeviceData() {
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.versionV3.versionGet())
    cmds.add(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1))
    cmds.addAll(processAssociations())
    cmds.addAll(pollConfigs())
    sendEvent(name: "numberOfButtons", value: 8)
    sendToDevice(cmds)
}

void refresh() {
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.switchMultilevelV2.switchMultilevelGet())
    cmds.add(zwave.basicV1.basicGet())
    sendToDevice(cmds)
}

void installed() {
    if (logEnable) log.debug "installed()..."
    initializeVars()
}

void eventProcess(Map evt) {
    if (device.currentValue(evt.name).toString() != evt.value.toString() || !eventFilter) {
        evt.isStateChange=true
        sendEvent(evt)
    }
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    if (logEnable) log.debug cmd
    switchEvents(cmd)
}

private void switchEvents(hubitat.zwave.Command cmd) {
    String value = (cmd.value ? "on" : "off")
    String description = "${device.displayName} was turned ${value}"
    if (txtEnable) log.info description
    eventProcess(name: "switch", value: value, descriptionText: description, type: state.isDigital?"digital":"physical")
    state.isDigital=false
}

void zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    if (logEnable) log.debug cmd
    switchEvents(cmd)
}

void on() {
    state.isDigital=true
    sendToDevice(zwave.basicV1.basicSet(value: 0xFF))
}

void off() {
    state.isDigital=true
    sendToDevice(zwave.basicV1.basicSet(value: 0x00))
}

void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
}

void parse(String description) {
    if (logEnable) log.debug "parse:${description}"
    hubitat.zwave.Command cmd = zwave.parse(description, CMD_CLASS_VERS)
    if (cmd) {
        zwaveEvent(cmd)
    }
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd) {
    if (logEnable) log.debug "Supervision get: ${cmd}"
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
    sendToDevice(new hubitat.zwave.commands.supervisionv1.SupervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0))
}

void zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.DeviceSpecificReport cmd) {
    if (logEnable) log.debug "Device Specific Report: ${cmd}"
    switch (cmd.deviceIdType) {
        case 1:
            // serial number
            def serialNumber=""
            if (cmd.deviceIdDataFormat==1) {
                cmd.deviceIdData.each { serialNumber += hubitat.helper.HexUtils.integerToHexString(it & 0xff,1).padLeft(2, '0')}
            } else {
                cmd.deviceIdData.each { serialNumber += (char) it }
            }
            device.updateDataValue("serialNumber", serialNumber)
            break
    }
}

void zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd) {
    if (logEnable) log.debug "version3 report: ${cmd}"
    device.updateDataValue("firmwareVersion", "${cmd.firmware0Version}.${cmd.firmware0SubVersion}")
    device.updateDataValue("protocolVersion", "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
    device.updateDataValue("hardwareVersion", "${cmd.hardwareVersion}")
}

void sendToDevice(List<hubitat.zwave.Command> cmds) {
    sendHubCommand(new hubitat.device.HubMultiAction(commands(cmds), hubitat.device.Protocol.ZWAVE))
}

void sendToDevice(hubitat.zwave.Command cmd) {
    sendHubCommand(new hubitat.device.HubAction(secureCommand(cmd), hubitat.device.Protocol.ZWAVE))
}

void sendToDevice(String cmd) {
    sendHubCommand(new hubitat.device.HubAction(secureCommand(cmd), hubitat.device.Protocol.ZWAVE))
}

List<String> commands(List<hubitat.zwave.Command> cmds, Long delay=200) {
    return delayBetween(cmds.collect{ secureCommand(it) }, delay)
}

String secureCommand(hubitat.zwave.Command cmd) {
    secureCommand(cmd.format())
}

String secureCommand(String cmd) {
    String encap=""
    if (getDataValue("zwaveSecurePairingComplete") != "true") {
        return cmd
    } else {
        encap = "988100"
    }
    return "${encap}${cmd}"
}

void zwaveEvent(hubitat.zwave.Command cmd) {
    if (logEnable) log.debug "skip:${cmd}"
}

List<hubitat.zwave.Command> setDefaultAssociation() {
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.associationV2.associationSet(groupingIdentifier: 1, nodeId: zwaveHubNodeId))
    cmds.add(zwave.associationV2.associationGet(groupingIdentifier: 1))
    return cmds
}

List<hubitat.zwave.Command> processAssociations(){
    List<hubitat.zwave.Command> cmds = []
    cmds.addAll(setDefaultAssociation())
    for (int i = 2; i<=numberOfAssocGroups; i++) {
        if (logEnable) log.debug "group: $i dataValue: " + getDataValue("zwaveAssociationG$i") + " parameterValue: " + settings."associationsG$i"
        String parameterInput=settings."associationsG$i"
        List<String> newNodeList = []
        List<String> oldNodeList = []
        if (getDataValue("zwaveAssociationG$i") != null) {
            getDataValue("zwaveAssociationG$i").minus("[").minus("]").split(",").each {
                if (it != "") {
                    oldNodeList.add(it.minus(" "))
                }
            }
        }
        if (parameterInput != null) {
            parameterInput.minus("[").minus("]").split(",").each {
                if (it != "") {
                    newNodeList.add(it.minus(" "))
                }
            }
        }
        if (oldNodeList.size > 0 || newNodeList.size > 0) {
            if (logEnable) log.debug "${oldNodeList.size} - ${newNodeList.size}"
            oldNodeList.each {
                if (!newNodeList.contains(it)) {
                    // user removed a node from the list
                    if (logEnable) log.debug "removing node: $it, from group: $i"
                    cmds.add(zwave.associationV2.associationRemove(groupingIdentifier: i, nodeId: Integer.parseInt(it, 16)))
                }
            }
            newNodeList.each {
                cmds.add(zwave.associationV2.associationSet(groupingIdentifier: i, nodeId: Integer.parseInt(it, 16)))
            }
        }
        cmds.add(zwave.associationV2.associationGet(groupingIdentifier: i))
    }
    if (logEnable) log.debug "processAssociations cmds: ${cmds}"
    return cmds
}


void zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
    if (logEnable) log.debug "${device.label?device.label:device.name}: ${cmd}"
    List<String> temp = []
    if (cmd.nodeId != []) {
        cmd.nodeId.each {
            temp.add(it.toString().format( '%02x', it.toInteger() ).toUpperCase())
        }
    }
    updateDataValue("zwaveAssociationG${cmd.groupingIdentifier}", "$temp")
}

void zwaveEvent(hubitat.zwave.commands.associationv2.AssociationGroupingsReport cmd) {
    if (logEnable) log.debug "${device.label?device.label:device.name}: ${cmd}"
    log.info "${device.label?device.label:device.name}: Supported association groups: ${cmd.supportedGroupings}"
    state.associationGroups = cmd.supportedGroupings
}

void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneNotification cmd) {
    Map evt = [name: "pushed", type:"physical", isStateChange:true]
    if (cmd.sceneNumber==1) {
        if (cmd.keyAttributes==0) {
            evt.value=1
            evt.descriptionText="${device.displayName} button ${evt.value} pushed"
            if (txtEnable) log.info evt.descriptionText
            sendEvent(evt)
        } else if (cmd.keyAttributes==3) {
            evt.value=3
            evt.descriptionText="${device.displayName} button ${evt.value} pushed"
            if (txtEnable) log.info evt.descriptionText
            sendEvent(evt)
        } else if (cmd.keyAttributes==4) {
            evt.value=5
            evt.descriptionText="${device.displayName} button ${evt.value} pushed"
            if (txtEnable) log.info evt.descriptionText
            sendEvent(evt)
        } else if (cmd.keyAttributes==5) {
            evt.value=7
            evt.descriptionText="${device.displayName} button ${evt.value} pushed"
            if (txtEnable) log.info evt.descriptionText
            sendEvent(evt)
        }
    } else if (cmd.sceneNumber==2) {
        if (cmd.keyAttributes==0) {
            evt.value=2
            evt.descriptionText="${device.displayName} button ${evt.value} pushed"
            if (txtEnable) log.info evt.descriptionText
            sendEvent(evt)
        } else if (cmd.keyAttributes==3) {
            evt.value=4
            evt.descriptionText="${device.displayName} button ${evt.value} pushed"
            if (txtEnable) log.info evt.descriptionText
            sendEvent(evt)
        } else if (cmd.keyAttributes==4) {
            evt.value=6
            evt.descriptionText="${device.displayName} button ${evt.value} pushed"
            if (txtEnable) log.info evt.descriptionText
            sendEvent(evt)
        } else if (cmd.keyAttributes==5) {
            evt.value=8
            evt.descriptionText="${device.displayName} button ${evt.value} pushed"
            if (txtEnable) log.info evt.descriptionText
            sendEvent(evt)
        }
    }
}
