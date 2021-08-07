# Hubitat-NOAA-Weather-Alert-Monitor
App and Driver for Hubitat (Groovy) to monitor local NOAA weather alerts and indicate the single "most urgent" alert in effect

This app and driver ("app and related code") are based on code from a similar app/driver from Aaron Ward.

Using that as an example, I developed this app and related code solely for my own use. As such, the app, the code, the functionality, and any bug fixing is done primarily (if not exclusively) for my own benefit. However, I'm making it available as an example and/or for others to use solely at their own and complete risk in case others may find it useful. Because this is not a commercial endeavor and I am not offering this for sale nor otherwise receiving any benefit from sharing this, the harsh warranty and liability information is intended to make clear that I am unable to accept responsibility for anything that results from anyone using this app, driver, or any of the related code.

This app and related code are NOT intended to replace trusted, reliable weather alerting device (e.g., weather radios).

=========

While the original app by Aaron Ward was seemingly designed to allow "speaking" any/all weather alerts via TTS, this app is intended to simply track the single, most urgent alert in effect for the location.

My purpose for this was to use it to trigger "status alerts" using the LED strip on Inovelli Red Dimmers to show when a serious weather condition is in progress (e.g., warnings show up as a "bouncing" orange notification, watches show as a pulsing orange notification, and other weather issues show as solid orange color on the strip). 

Also, the "recheck" timing is always set to 1 minute IF there are any existing alerts in effect. The timing for rechecking when NO alert are in progress is changeable in the app settings. This was done to help ensure that serious weather events are detected more quickly (e.g., if there is a "Severe Thunderstorm Watch" in effect, it will re-check every minute to help detect if/when a "Tornado Warning" or "Severe Thunderstorm Warning" is issued. The assumption is that there are normally watches in effect before warnings are issued.

The virtual device will have summary details about what is considered the most urgent event (in general, warnings>watches>other events). 

=========

NOTICE ABOUT WARRANTIES AND LIABILITY:

By using this app, driver, or any related code in any manner whatsoever, you fully and completely agree to the following:

1) YOU ARE FULLY RESPONSIBLE FOR ALWAYS HAVING A RELIABLE AND TRUSTED METHOD FOR OBTAINING WEATHER ALERTS. THIS APP AND RELATED CODE >>ARE NOT<< TO BE CONSIDERED RELIABLE OR TRUSTED METHODS FOR OBTAINING ANY WEATHER ALERTS.

2) YOU take FULL AND COMPLETE responsibility for ANY AND ALL use of this app and related code and agree that ALL responsibility and liability for ANY use you make of the app or related code will be TOTALLY as if YOU developed this ENTIRELY ON YOUR OWN.

3) This app and related code is being provided freely for anyone's use with absolutely NO GUARANTEES, NO WARRANTIES, NO LIABILITY ASSUMED, NO RESPONSIBIILITY whatsoever. 

4) There are no guarantees, including but not limited to: no guarantees that it will work, no guarantees that it will work properly when desired/necessary, no guarantees that it will be bug free, no guarantees that it will not cause impact or harm to your hub or any other device. This app and related code are provided solely under the condition that they are TOTALLY USE AT YOUR OWN RISK.

5) There is NO GUARANTEE, NO LIABILITY, NO RESPONSIBILITY assumed for providing ANY future updates to this app nor to any of the related code.

If these terms are not acceptable to you or otherwise not allowed (e.g., by your location, etc.), then you SHALL NOT MAKE ANY USE OF THIS APP OR RELATED CODE IN ANY MANNER.

=========

LICENSING NOTE

As the original code came from Aaron Ward, this carries the license from his code.

*  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License. You may obtain a copy of the License at:
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*  for the specific language governing permissions and limitations under the License.
