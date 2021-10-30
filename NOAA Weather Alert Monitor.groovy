/*  **************** NOAA Weather Alerts ****************
 *
 *  Hubitat Import URL:
 *
 *  Copyright 2019 Aaron Ward
 *  Copyright 2021 Robert L. Stitt
 *
 *-------------------------------------------------------------------------------------------------------------------
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * Last Update: 10/08/2021
 *   - Correct case on array values passed to NOAA API
 *   - Use "alerts/active" API so only active alerts are returned
 *
 * Last Update: 10/30/2021
 *   - Correct case on array values used elsewhere, related to the NOAA API
 *   - Correct the app's description in the header
 */

static String version() { return "1.0.003" }

import groovy.transform.Field
import groovy.json.*
import java.util.regex.*
import java.text.SimpleDateFormat
import java.text.ParseException
import java.util.Date
import groovy.time.*

definition(
   name:"NOAA Weather Alert Monitor",
   namespace: "robstitt",
   author: "Rob Stitt",
   description: "NOAA Weather Alert Monitor Application ",
   category: "Weather",
   iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
   iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
   iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
// documentationLink: "https://github.com/imnotbob/Hubitat-4/blob/master/NOAA/README.md",
   singleInstance: true,
   oauth: false,
   pausable: true)

preferences {
   page name: "mainPage", title: "", install: true, uninstall: false
   page name: "ConfigPage", title: "", install: false, uninstall: false, nextPage: "mainPage"
   page name: "DebugPage", title: "", install: false, uninstall: true, nextPage: "mainPage"
}

@Field static Map LastAlertResult=[:]
@Field static Map HighestAlert=[:]
@Field static Map SavedRealAlert=[:]
@Field static boolean TestInProgress=false

def mainPage() {
   dynamicPage(name: "mainPage") {
      installCheck()
      if((String)state.appInstalled == 'COMPLETE') {
         section(UIsupport("logo","")) {
            if(whatAlertSeverity || whatPoll || monitoredWeatherEvents || whatAlertUrgency || whatAlertCertainty) href(name: "ConfigPage", title: "${UIsupport("configured","")} Weather Alert Settings", required: false, page: "ConfigPage", description: "Settings for the weather alerts to be monitored")
            else  href(name: "ConfigPage", title: "${UIsupport("attention","")} Weather Alert Settings", required: false, page: "ConfigPage", description: "Change default settings for weather alerts to monitor")

            href(name: "DebugPage", title: "Debugging", required: false, page: "DebugPage", description: "Debug and Test Options")
            paragraph UIsupport("line","")
            paragraph UIsupport("footer","")
         }
      }
   }
}

def ConfigPage() {
   dynamicPage(name: "ConfigPage") {
      section(UIsupport("logo","")) {
         paragraph UIsupport("header", " Weather Event Settings")
         paragraph "Configure the event types and other values, how often to poll for weather information, set custom coordinates, set a switch to use when monitored events are detected."

         buildEventsList()

         input "monitoredWeatherEvents", "enum", title: "Select all weather events to monitor: ", required: false, multiple: true, submitOnChange: true,
            options: state.eventTypes

         input name: "whatAlertSeverity", type: "enum", title: "Monitored severities: ", required: true, multiple: true, submitOnChange: true,
            options: [
               "Minor": "Minor",
               "Moderate": "Moderate",
               "Severe": "Severe",
               "Extreme": "Extreme"
            ], defaultValue: ["Severe", "Extreme"]

         input name: "whatAlertUrgency", type: "enum", title: "Monitored urgencies (recommend all): ", required: true, multiple: true, submitOnChange: true,
            options: [
               "Immediate": "Immediate",
               "Expected": "Expected",
               "Future": "Future"
            ], defaultValue: ["Immediate", "Expected", "Future"]

         input name: "whatAlertCertainty", type: "enum", title: "Monitored certainties (recommend all): ", required: true, multiple: true, submitOnChange: true,
            options: [
               "Possible": "Possible",
               "Likely": "Likely",
               "Observed": "Observed"
            ], defaultValue: ["Possible", "Likely", "Observed"]

         input name: "whatPoll", type: "enum", title: "Poll Frequency (when no alerts are active; always 1 min when watches or warnings are active): ", required: true, multiple: false, submitOnChange: true,
            options: [
               "1": "1 Minute",
               "5": "5 Minutes",
               "10": "10 Minutes",
               "15": "15 Minutes",
               "30": "30 Minutes"
            ], defaultValue: "5"

         setRefresh()

         input name: "useCustomCords", type: "bool", title: "Use Custom Coordinates?", require: false, defaultValue: false, submitOnChange: true

         if(useCustomCords) {
            paragraph "The default coordinates are acquired from your Hubitat Hub.  Enter your custom coordinates:"
            input name:"customlatitude", type:"text", title: "Latitude coordinate:", require: false, defaultValue: "${location.latitude}", submitOnChange: true
            input name:"customlongitude", type:"text", title: "Longitude coordinate:", require: false, defaultValue: "${location.longitude}", submitOnChange: true
         }
         // Switch to set when alert active
         input (name: "UsealertSwitch", type: "bool", title: "Use a switch to turn ON with Alert?", required: false, defaultValue: false, submitOnChange: true)
         if(UsealertSwitch) {
            input (name: "alertSwitch", type: "capability.switch", title: "Select a switch to turn ON with Alert?", multiple: false, required: false, defaultValue: false, submitOnChange: true)
         }

         main()
      }
   }
}

def DebugPage() {
   dynamicPage(name: "DebugPage") {
      section(UIsupport("logo","")) {
         paragraph UIsupport("header", " Debug and Test Options")
         paragraph "Enable logging, run a test alert, if errors reset the applications state settings and test your weather alert configurations."

         input "logEnable", "bool", title: "Enable Normal Logging?", required: false, defaultValue: true, submitOnChange: true

         input "debugEnable", "bool", title: "Enable Debug Logging?", required: false, defaultValue: false, submitOnChange: true

         input "runTest", "enum", title: "Run a test Alert?", required: true, multiple: false, submitOnChange: true,
            options: [
               "no": "No",
               "watch": "Watch",
               "warning": "Warning"
            ], defaultValue: "No"
         if(runTest!=null && runTest!="no") {
            runtestAlert(runTest)
            app.updateSetting("runTest",[value:"no",type:"enum"])
         }

         input "init", "bool", title: "Reset the current application state?", required: false, submitOnChange: true, defaultValue: false
         if(init) {
            app.updateSetting("init",[value:"false",type:"bool"])
            if(UsealertSwitch && alertSwitch && alertSwitch.currentState("switch").value == "on") alertSwitch.off()
            if (logEnable) log.warn "NOAA Weather Alert Monitor application state has been reset and alerts cleared."
            initialize()
         }

         input "testAPI", "bool", title: "Invoke and test the NOAA Weather Alert API now?", required: false, submitOnChange: true, defaultValue: false
         if(testAPI) {
            app.updateSetting("testAPI",[value:"false",type:"bool"])

            getAlertMsg()

            if(HighestAlert.text!="None") {
               Date date = new Date()
               SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy h:mm a")

               temp = "<hr><br>Current poll of Weather API: ${sdf.format(date)}<br/><br/>URI: <a href='${state.wxURI}' target=_blank>${state.wxURI}</a><br><br>"
               paragraph temp

               String testConfig = ""

               testConfig += "<table border=1px>"
               testConfig += "<tr><td>Text</td><td>Value</td></tr><tr><td>Severity</td><td>${HighestAlert.alertseverity}</td></tr>"
               testConfig += "<tr><td>Level</td><td>${HighestAlert.text}</td></tr>"
               testConfig += "<tr><td>Urgency</td><td>${HighestAlert.urgency}</td></tr>"
               testConfig += "<tr><td>Certainty</td><td>${HighestAlert.certainty}</td></tr>"
               testConfig += "<tr><td>Severity</td><td>${HighestAlert.severity}</td></tr>"
               testConfig += "<tr><td>Event Type</td><td>${HighestAlert.eventtype}</td></tr>"
               testConfig += "<tr><td>Expires</td><td>${HighestAlert.expires}</td></tr>"
               paragraph testConfig
            }
            else paragraph "There are no reported weather alerts in your area, the weather alerts available have expired, the api.weather.gov api is not available, or you need to change NOAA Weather Alert Monitor options to acquire desired results.<br><br>Current URI: <a href='${state.wxURI}' target=_blank>${state.wxURI}</a>"

            setAlertSwitch()
            callRefreshAlertDevice()
            setRefresh()
         }
      }
   }
}

// Main Application Routines
def main() {
    // Get the current alert info if a test alert isn't currently active
    if (!TestInProgress) {
        if (HighestAlert==null || HighestAlert.text==null || HighestAlert.level==null) {
            if(debugEnable) log.debug "Initializing Highest Alert variable (it was null)"
            HighestAlert = emptyAlert()
        }
        Map LastAlert = HighestAlert.clone()
        getAlertMsg()

        if (HighestAlert.equals(LastAlert)) {
            if(debugEnable && HighestAlert.text!=null && HighestAlert.text!="None") log.debug "Weather alert is unchanged: ${HighestAlert.level}(${HighestAlert.text})"
        } else {
            setAlertSwitch()
            callRefreshAlertDevice()
            if(logEnable) log.info "New weather alert: ${HighestAlert.level}(${HighestAlert.text}), was: ${LastAlert.level}(${LastAlert.text})"
            setRefresh()
        }
    }
}

void callRefreshAlertDevice(){
   def noaaAlertDevice = getChildDevice("NOAAwxalert")
    if(noaaAlertDevice) {
       noaaAlertDevice.refresh()
    } else {
       if(logEnable) log.warn "No child alert device found to refresh"
    }
}

void setAlertSwitch(){
   if(HighestAlert.text!=null && HighestAlert.text!="None"){
       if(UsealertSwitch && alertSwitch && alertSwitch.currentState("switch").value == "off") {
           if(logEnable) log.info "Turning the weather alert switch On"
           alertSwitch.on()
       }
   } else {
       if(UsealertSwitch && alertSwitch && alertSwitch.currentState("switch").value == "on") {
           if(logEnable) log.info "Turning the weather alert switch Off"
           alertSwitch.off()
       }
   }
}

Map emptyAlert() {
  return [text:"None", level:"-", urgency:"-", certainty:"-", severity:"-", eventtype:"-", expires:"-"]
}

void getAlertMsg() {
   Map result = getWeatherAlerts()
   Map CurrentHighestAlert = emptyAlert()

   Map levelmap     = [other:1, watch:2, warning:3]
   Map urgencymap   = [future:1, expected:2, immediate:3]
   Map certaintymap = [possible:1, likely:2, observed:3]
   Map severitymap  = [minor:1, moderate:2, severe:3, extreme:4]

   if(result) {
      Date curtime = new Date()
      String timestamp = curtime.format("yyyy-MM-dd'T'HH:mm:ssXXX")

      if (debugEnable) log.debug "Weather API returned ${result.features.size().toString()} entries"
      for(i=0; i<result.features.size();i++) {
         if(debugEnable) log.debug "In weather event loop, i=${i.toString()}"

         String  alertstatus
         String  alerttext
         String  alerturgency
         String  alertcertainty
         String  alertseverity
         String  alerteventtype
         String  alertlevel
         String  alertstarts
         String  alertexpires
         Date    dtalertstarts
         Date    dtalertexpires
         Integer alerturgencynum
         Integer alertcertaintynum
         Integer alertseveritynum
         Integer alertlevelnum

         alertstatus    = (String)result.features[i].properties.status
         alerttext      = (String)result.features[i].properties.headline
         alerturgency   = result.features[i].properties.urgency
         alertcertainty = result.features[i].properties.certainty
         alertseverity  = result.features[i].properties.severity
         alerteventtype = result.features[i].properties.event

         if(debugEnable) log.debug "Status: $alertstatus"
         if(debugEnable) log.debug "Text: $alerttext"
         if(debugEnable) log.debug "Urgency: $alerturgency"
         if(debugEnable) log.debug "Certainty: $alertcertainty"
         if(debugEnable) log.debug "Severity: $alertseverity"
         if(debugEnable) log.debug "EventType: $alerteventtype"

         if      (alerttext.toLowerCase().contains("warning")) alertlevel = "Warning"
         else if (alerttext.toLowerCase().contains("watch")) alertlevel = "Watch"
         else if (alerturgency=="immediate" || alertcertainty=="observed") alertlevel="Warning"
         else if (alerturgency=="expected" || alertcertainty=="likely") alertlevel="Watch"
         else alertlevel="Other"

         if(debugEnable) log.debug "Level: $alertlevel"

         //alert starts
         if(result.features[i].properties.onset) alertstarts = result.features[i].properties.onset
         else if(result.features[i].properties.effective) alertstarts = result.features[i].properties.effective
         else if(result.features[i].properties.sent) alertstarts = result.features[i].properties.sent
         else alertstarts = timestamp

         if(debugEnable) log.debug "Starts: $alertstarts"

         try {
            dtalertstarts = Date.parse("yyyy-MM-dd'T'HH:mm:ssXXX", alertstarts)
         }
         catch (e) {
            log.error "Error parsing weather alert start date ${alertstarts}."
            dtalertstarts = curtime
         }

         //alert expiration
         if(result.features[i].properties.ends) alertexpires = result.features[i].properties.ends
         else alertexpires = result.features[i].properties.expires

         if(debugEnable) log.debug "Expires: $alertexpires"

         try {
            dtalertexpires = Date.parse("yyyy-MM-dd'T'HH:mm:ssXXX", alertexpires)
         }
         catch (e) {
            log.error "Error parsing weather alert expiration date ${alertexpires} (assuming 1 hour from now)."
            dtalertexpires = curtime + 1.hour
         }

         if(debugEnable) log.debug "Found Weather Alert: ${alerttext}: Alert Starts: ${alertstarts}, Alert Expires: ${alertexpires}, Current time: ${timestamp}"

         // Only deal with this alert if it is actual and has started and hasn't expired yet
         if((alertstatus="Actual") && (dtalertstarts <= curtime) && (dtalertexpires > curtime)) {
             if(debugEnable) log.debug "Alert is active: ${alerttext}"

            // Only review this alert if it is a monitored event type
            if (   ( !(monitoredWeatherEvents) || (monitoredWeatherEvents.size() == 0)
                     || (monitoredWeatherEvents && monitoredWeatherEvents*.toLowerCase().contains(alerteventtype.toLowerCase())) )
                && ( !(whatAlertSeverity) || (whatAlertSeverity.size() == 0)
                     || (whatAlertSeverity && whatAlertSeverity*.toLowerCase().contains(alertseverity.toLowerCase())) )
                && ( !(whatAlertUrgency) || (whatAlertUrgency.size() == 0)
                     || (whatAlertUrgency && whatAlertUrgency*.toLowerCase().contains(alerturgency.toLowerCase())) )
                && ( !(whatAlertCertainty) || (whatAlertCertainty.size() == 0)
                     || (whatAlertCertainty && whatAlertCertainty*.toLowerCase().contains(alertcertainty.toLowerCase())) )  ) {

               if(debugEnable) log.debug "Alert is a selected event type: ${alerttext}"

               boolean thisishigher = false

               // Compare the current alert to the highest alert previously returned
               if (CurrentHighestAlert.text=="None") {
                  thisishigher = true
               } else {
                  Integer higherlevel     = -1
                  Integer higherurgency   = -1
                  Integer highercertainty = -1
                  Integer higherseverity  = -1

                  try {
                     higherlevel = levelmap.get(alertlevel.toLowerCase()).compareTo(levelmap.get(CurrentHighestAlert.level.toLowerCase()))
                  }
                  catch (e) {higherlevel = 1}

                  try {
                     higherurgency = urgencymap.get(alerturgency.toLowerCase()).compareTo(urgencymap.get(CurrentHighestAlert.urgency.toLowerCase()))
                  }
                  catch (e) {higherurgency = 1}

                  try {
                     highercertainty = certaintymap.get(alertcertainty.toLowerCase()).compareTo(certaintymap.get(CurrentHighestAlert.certainty.toLowerCase()))
                  }
                  catch (e) {highercertainty = 1}

                  try {
                     higherseverity = severitymap.get(alertseverity.toLowerCase()).compareTo(severitymap.get(CurrentHighestAlert.severity.toLowerCase()))
                  }
                  catch (e) {higherseverity = 1}

                  if(debugEnable) log.debug "Alert compared to previous: Level=${higherlevel}, Urgency=${higherurgency}, Certainty=${highercertainty}, Severity=${higherseverity}"

                  if (((alerturgency.toLowerCase() == "immediate") || (alertcertainty.toLowerCase() == "observed")) && (alertlevel.toLowerCase() == "warning")) {
                     if (higherseverity==1) thisishigher = true
                  } else if (higherlevel==1) {
                     thisishigher = true
                  } else if ((higherlevel==0) && (higherseverity==1)) {
                     thisishigher = true
                  } else if ((higherlevel==0) && (higherseverity==0) && (higherurgency==1)) {
                     thisishigher = true
                  } else if ((higherlevel==0) && (higherseverity==0) && (higherurgency==0) && (highercertainty==1)) {
                     thisishigher = true
                  }
               }

               if (thisishigher) {
                  if(debugEnable) log.debug "Alert is highest so far: ${alerttext}"
                  CurrentHighestAlert.text      = alerttext
                  CurrentHighestAlert.level     = alertlevel
                  CurrentHighestAlert.urgency   = alerturgency
                  CurrentHighestAlert.certainty = alertcertainty
                  CurrentHighestAlert.severity  = alertseverity
                  CurrentHighestAlert.eventtype = alerteventtype
                  CurrentHighestAlert.expires   = alertexpires
               }
               else if(debugEnable) log.debug "Alert is NOT highest so far: ${alerttext}"
            }
            else if(debugEnable) log.debug "Alert is NOT a monitored event type: ${alerttext}"
         }
         else if(debugEnable) log.debug "Alert is NOT active: ${alerttext}"
      } //end of for statement
   }

   if(result==null) { // deal with network outages and errors by only making updates if something was returned
      if(logEnable) log.warn "No data returned from the weather alert request"
   } else {
      if(logEnable && (HighestAlert.text==null || HighestAlert.level==null || (HighestAlert && CurrentHighestAlert && !HighestAlert.equals(CurrentHighestAlert)))) log.info "Highest Alert is now: ${CurrentHighestAlert.level}(${CurrentHighestAlert.text}), was: ${HighestAlert.level}(${HighestAlert.text})"
      HighestAlert = CurrentHighestAlert.clone()
   }
}

void runtestAlert(level) {
    TestInProgress=true
    if (HighestAlert==null || HighestAlert.text==null || HighestAlert.level==null) {
        if (debugEnable) log.debug "Initializing Highest Alert variable (it was null)"
        HighestAlert = emptyAlert()
    }

    SavedRealAlert = HighestAlert.clone()
    HighestAlert = emptyAlert()
    if(logEnable) log.info "Initiating a test ${level} alert (saving the current Highest Alert: ${SavedRealAlert.level}(${SavedRealAlert.text}))"
    if(level=="warning") HighestAlert=buildTestWarningAlert()
    if(level=="watch")   HighestAlert=buildTestWatchAlert()
    setAlertSwitch()
    callRefreshAlertDevice()
    runIn(30,endTest)
}

void endTest(){
   HighestAlert = SavedRealAlert.clone()
   SavedRealAlert = emptyAlert()
   TestInProgress=false
   if(logEnable) log.info "Ending test alert, restoring the Highest Alert to: ${HighestAlert.level}(${HighestAlert.text})"
   setAlertSwitch()
   callRefreshAlertDevice()
}

Map buildTestWatchAlert() {
   Date date = new Date()
   String timestamp = date.format("yyyy-MM-dd'T'HH:mm:ssXXX")
   return [text:"Test Watch", level:"Watch", urgency:"Expected", certainty:"Likely", severity:"Severe", eventtype:"Test Watch", expires:"${timestamp}"]
}

Map buildTestWarningAlert() {
   Date date = new Date()
   String timestamp = date.format("yyyy-MM-dd'T'HH:mm:ssXXX")
   return [text:"Test Warning", level:"Warning", urgency:"Immediate", certainty:"Observed", severity:"Severe", eventtype:"Test Warning", expires:"${timestamp}"]
}

Map getAlertDevice() {
   if(debugEnable) log.debug "Returning HighestAlert to child device: ${HighestAlert.level}(${HighestAlert.text})"
   return HighestAlert
}

void buildEventsList() {
   Map results = getResponseEvents()
   if(results) {
      state.eventTypes = (List)results.eventTypes
      if(debugEnable) log.debug "Acquired current events list from api.weather.gov"
   }
   //unschedule(buildEventsList)
   //schedule("00 00 01 ? * *", buildEventsList) // once a day 1:00 AM
}

Map getResponseEvents() {
   String wxURI = "https://api.weather.gov/alerts/types"
   Map result = null
   Map requestParams =  [
      uri:"${wxURI}",
      requestContentType:"application/json",
      contentType:"application/json"
   ]

   try {
      httpGet(requestParams)  { response -> result = response.data}
   }
   catch (e) { if(logEnable) log.warn "The API Weather.gov did not return a response when asked for all possible event types, exception: $e." }
   return result
}

// Device creation and status updhandlers
void createChildDevices() {
   try {
      if (!getChildDevice("NOAAwxalert")) {
         if (logEnable) log.info "Creating device: NOAA Weather Alert Device"
         addChildDevice("robstitt", "NOAA Weather Alert Device", "NOAAwxalert", 1234, ["name": "NOAA Weather Alert Device", isComponent: false])
      }
   }
   catch (e) { log.error "Couldn't create child device. ${e}" }
}

void cleanupChildDevices() {
   try {
      for(device in getChildDevices()) deleteChildDevice(device.deviceNetworkId)
   }
   catch (e) { log.error "Couldn't clean up child devices." }
}

// Application Support Routines
Map getWeatherAlerts() {
   // Determine if custom coordinates have been selected
   String latitude
   String longitude
   if(useCustomCords) {
      latitude = "${customlatitude}".toString()
      longitude = "${customlongitude}".toString()
   } else {
      latitude = "${location.latitude}".toString()
      longitude = "${location.longitude}".toString()
   }

   String wxURI = "https://api.weather.gov/alerts/active?point=${latitude}%2C${longitude}&status=actual&message_type=alert".toString()
   Map result = [:]

   // Build out the API options
   if(whatAlertUrgency != null) wxURI = wxURI + "&urgency=${whatAlertUrgency.join(",")}".toString()

   if(whatAlertSeverity != null) wxURI = wxURI + "&severity=${whatAlertSeverity.join(",")}".toString()
   else wxURI = wxURI + "&severity=Severe"

   if(whatAlertCertainty !=null) wxURI = wxURI + "&certainty=${whatAlertCertainty.join(",")}".toString()

   state.wxURI = wxURI
   if(debugEnable) log.debug "URI: <a href='${wxURI}' target=_blank>${wxURI}</a>"


   if(debugEnable) log.debug "Connecting to weather.gov service."
   Map requestParams =  [
      uri:"${wxURI}",
      requestContentType:"application/json",
      contentType:"application/json"
   ]

   try {
      httpGet(requestParams)  { response -> result = response.data }
   }
   catch (e) {
      if(logEnable) log.warn "The API Weather.gov did not return a response (retaining previous results), exception: $e"
      result = LastAlertResult.clone()
   }

   LastAlertResult = result.clone()

   return result
}

void checkState() {
   if(whatPoll==null) app.updateSetting("whatPoll",[value:"5",type:"enum"])
   if(logEnable==null) app.updateSetting("logEnable",[value:"false",type:"bool"])
   if(debugEnable==null) app.updateSetting("debugEnable",[value:"false",type:"bool"])
   if(logMinutes==null) app.updateSetting("logMinutes",[value:15,type:"number"])
   if(whatAlertSeverity==null) app.updateSetting("whatAlertSeverity",[value:["Severe","Extreme"],type:"enum"])
   if(whatAlertUrgency==null) app.updateSetting("whatAlertUrgency",[value:["Immediate", "Expected", "Future"],type:"enum"])
   if(whatAlertCertainty==null) app.updateSetting("whatAlertCertainty",[value:["Possible", "Likely", "Observed"],type:"enum"])
}

void installCheck(){
   state.appInstalled = app.getInstallationState()
   if((String)state.appInstalled != 'COMPLETE'){
      section{paragraph "Please hit 'Done' to install ${app.label} "}
   }
}

void initialize() {
    checkState()
    unschedule()
    //buildEventsList()
    TestInProgress=false
    HighestAlert=emptyAlert()
    SavedRealAlert=emptyAlert()
    createChildDevices()
    callRefreshAlertDevice()
    runIn(2,main)
    runIn(5,setRefresh)
}

void setRefresh() {
   unschedule()

   Integer myPoll=5
   if(whatPoll!=null) myPoll=whatPoll.toInteger()
   if(HighestAlert.text!="None" && (HighestAlert.level=="Watch" || HighestAlert.level=="Warning") && myPoll>1) {
       myPoll=1
       if (debugEnable) log.debug "Polling interval overridden and set to 1 minute due to active watch or warning"
   } else if (debugEnable) log.debug "Polling interval set to ${myPoll} minute(s)"
   switch(myPoll) {
      case 1:
         runEvery1Minute(main)
         break
      case 10:
         runEvery10Minutes(main)
         break
      case 15:
         runEvery15Minutes(main)
         break
      case 30:
         runEvery30Minutes(main)
         break
      default:
         runEvery5Minutes(main)
         break
   }
}

void installed() {
   if(debugEnable) log.debug "Installed with settings: ${settings}"
   initialize()
}

void updated() {
   if(debugEnable) log.debug "Updated with settings: ${settings}"
   state.remove('num')
   initialize()
}

void uninstalled() {
   cleanupChildDevices()
}


static String UIsupport(String type, String txt) {
   switch(type) {
      case "logo":
         return "<table border=0><thead><tr><th><img border=0 style='max-width:100px' src='https://raw.githubusercontent.com/imnotbob/Hubitat-4/master/NOAA/Support/NOAA.png'></th><th style='padding:10px' align=left><font style='font-size:34px;color:#1A77C9;font-weight: bold'>NOAA Weather Alerts</font><br><font style='font-size:14px;font-weight: none'>This application retrieves information on the highest, currently effective weather alert.</font></tr></thead></table><br><hr style='margin-top:-15px;background-color:#1A77C9; height: 1px; border: 0;'></hr>"
         break
      case "line":
         return "<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
         break
      case "header":
         return "<div style='color:#ffffff;font-weight: bold;background-color:#1A7BC7;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${txt}</div>"
         break
      case "footer":
         return "<div style='color:#1A77C9;text-align:center'>App/Driver v${version()}<br>Written by: Robert L. Stitt; Based on code originally developed by: Aaron Ward<br></div>"
         break
      case "configured":
         return "<img border=0 style='max-width:15px' src='https://raw.githubusercontent.com/imnotbob/Hubitat-4/master/support/images/Checked.svg'>"
         break
      case "attention":
         return "<img border=0 style='max-width:15px' src='https://raw.githubusercontent.com/imnotbob/Hubitat-4/master/support/images/Attention.svg'>"
         break
   }
}
