# kplug

### create sample app
```shell script
git clone https://github.com/mallumoSK/kplug.git
cordova create test-app com.example.hello HelloWorld
cd test-app

# cordova platform remove android@9.0.0
cordova platform add android@9.0.0
cordova build

#cordova plugin remove tk.mallumo.cordova.kplug
cordova plugin add ../kplug

chrome://inspect/#devices
```

## Cordova requirments
* enabled kotlin v1.4.10
* cordove min. version 9.0.0

In config:
```xml
<preference name="GradlePluginKotlinEnabled" value="true" />
<preference name="GradlePluginKotlinCodeStyle" value="official" />
<preference name="GradlePluginKotlinVersion" value="1.4.10" />
<preference name="AndroidXEnabled" value="true" />
```

##### chrome inspect window 
chrome://inspect/#devices

## Test of working
```js
kplug.greet("World",function(resp){console.log(resp)},function(err){console.log("error:xxx ->" + err)})

sttBG.greet("World",function(resp){console.log(resp)},function(err){console.log("error:xxx ->" + err)})
```

## 1. Speech to text BG

#### Permission ``android.permission.RECORD_AUDIO`` must be granted before any function is called

Recognition is running on service

### 1.1. START
```js
sttBG.start({
	preferOffline: false,
	maxResults: 10	
	},function(resp){console.log(resp)},function(err){console.log("error:xxx ->" + err)})
```
### 1.2. STOP
```js
sttBG.stop()
```
### 1.3. STOP IMMEDIATELY
```js
sttBG.stopForce()
```

## 2. Speech to text FG

Recognition is running inside dialog

#### Permission ``android.permission.RECORD_AUDIO`` must be granted before any function is called

### 2.1. START
```js
sttFG.start({
	preferOffline: false,
	autoContinue: true,
	enableStartStopSound: true
	},function(resp){console.log(resp)},function(err){console.log("error:xxx ->" + err)})
```

Parameter **autoContinue** 
* **true** recognition is running nonstop until 
	* stop function is called 
	* app going to background 
	* error not in ERROR_NO_MATCH(7) , ERROR_SPEECH_TIMEOUT(6)
* **false** recognition is running 
	* until recognizing service invoke autoclese
	* app going to background 
	* any error

Parameter **enableStartStopSound** 
* **true** beep on start, stop, error
* **false** no beep
	* BUT also no sound of incoming notifications, during recognition runtime

### 2.2. STOP
```js
sttFG.stop()
```
### 2.3. TITLE TEXT
put title text into dialog 
```js
sttFG.text('<h1><font color="#009688">123642845874</font></h1>')
```

### 2.4. Colors
Change color of button background and animation flow
```js
sttFG.color('#009688','#e0f2f1')
```

### 2.5. reset recorded text
**Request**:
```js
sttFG.resetText()
```

## 3. Notification scheduler
### 3.1 Schedule
```js
kplug.schedule({
	id: 'some-unique-identifier',
	channel: 'name-of-notification-channel',
	channelImportance: 5, // from 0 to 5 
	priority: 2, // from -2 to 2
	img: 'mipmap.ic_launcher',
	title: 'title of notification',
	subtitle: 'subtitle of notification',
	color: '#f44336', //colorstyle
	vibrate: true,
	sound: true,
	led: true,
	time: 1600445854904, // schedule time in milliseconds,
	contentAction: {
		params: {
			param1: 'param1-value',
			param2: 'param2-value'		
		}
	},
	actions: [
		{
			broadcastKey: 'kplug-default',		
			img: 'mipmap.ic_launcher',
			title: 'action-1-name',
			params: {
				param1: 'param1-value',
				param2: 'param2-value'		
			}
		
		},
		{
			broadcastKey: 'special-local-broadcast-key-xyz',	
			activityPck: 'com.example.hello',
			activityClass: 'com.example.hello.MainActivity',
			img: 'mipmap.ic_launcher',
			title: 'action-2-name',
			params: {
				param1: 'param1-value',
				param2: 'param2-value'		
			}
		
		}
	]
},function(resp){console.log(resp)},function(err){console.log("error:xxx ->" + err)})
```

### 3.2 Stop schedule
```js
kplug.scheduleCancel("some-unique-identifier",function(resp){console.log(resp)},function(err){console.log("error:xxx ->" + err)})
```

## 4. Permissions

### 4.1 show all permissions used in project
**Request**:
```js
permission.listAll(function(resp){console.log(resp)})
```
**Result**:
```json
[
	{
	"first":"android.permission.ACCESS_NETWORK_STATE", /*name of permission*/
	"second":true /* permission is or not granted (true/false)*/
	},	
	{
	"first":"android.permission.RECORD_AUDIO", /*name of permission*/
	"second":true /* permission is or not granted (true/false)*/
	},	
	{
	"first":"android.permission.READ_EXTERNAL_STORAGE",
	"second":false /* permission is or not granted (true/false)*/
	},
]
```

### 4.2 show enabled permissions used in project
**Request**:
```js
permission.listEnabled(function(resp){console.log(resp)})
```
**Result**:
```json
[
"android.permission.ACCESS_NETWORK_STATE",
"android.permission.RECORD_AUDIO"
]
```

### 4.3 show disabled permissions used in project
**Request**:
```js
permission.listDisabled(function(resp){console.log(resp)})
```
**Result**:
```json
[
"android.permission.READ_EXTERNAL_STORAGE"
]
```

### 4.4 Enable single permission

**Request**:
```js
permission.enableSingle("android.permission.READ_EXTERNAL_STORAGE",function(resp){console.log(resp)})
```

**Result is array of REJECTED permissiond**

**Result IF user accept**:
```json
[]
```

**Result IF user reject**:
```json
[
"android.permission.READ_EXTERNAL_STORAGE"
]
```

### 4.5 Enable multiple permissions

**Request**:
```js
permission.enableMultiple(["android.permission.RECORD_AUDIO","android.permission.READ_EXTERNAL_STORAGE"], function(resp){console.log(resp)})
```

**Result is array of REJECTED permissions**

**Result IF user accept**:
```json
[]
```

**Result IF user reject**:
```json
[
"android.permission.READ_EXTERNAL_STORAGE"
]
```

### 4.6 Enable all project permissions

**Request**:
```js
permission.enableAll(function(resp){console.log(resp)})
```

**Result is array of REJECTED permissions**

**Result IF user accept**:
```json
[]
```

**Result IF user reject**:
```json
[
"android.permission.READ_EXTERNAL_STORAGE"
]
```
## 5. Sound ON/OFF

**Sound type:**
* 0 -> SYSTEM
* 1 -> MUSIC
* 2 -> ALARM
* 3 -> NOTIFICATION
* 4 -> DTMF
* 5 -> VOICE_CALL
* 6 -> RING
* 7 -> ACCESSIBILITY

### 5.1 Mute sound
```js
kplug.soundMuteON(0,function(resp){console.log(resp)},function(err){console.log("error:xxx ->" + err)})
```

### 5.2 Unmute sound
```js
kplug.soundMuteOFF(0,function(resp){console.log(resp)},function(err){console.log("error:xxx ->" + err)})
```

## 6 GPS

#### Permissions ``android.permission.ACCESS_FINE_LOCATION`` and ``android.permission.ACCESS_COARSE_LOCATION`` must be granted before any function is called

### 6.1 Start

start with default parameters

```js
loc.start({},function(resp){console.log(resp)},function(err){console.log("error:xxx ->" + err)})
```

### 6.1.1 Start with all parameters

```js
loc.start({
	identifier: "unique GPS start call ID xyz",
	minTimeMS: 10000,
	minDistanceM: 0,
	horizontalAccuracy: 0,
	verticalAccuracy: 0,
	speedAccuracy: 0,
	bearingAccuracy: 0,
	powerRequirement: 0,
	altitudeRequired: false,
	bearingRequired: false,
	speedRequired: false,
	costAllowed: false
},function(resp){console.log(resp)},function(err){console.log("error:xxx ->" + err)})
```
### 6.1.2 Start request object definition
```kotlin
data class LocationRequest(
        /**
         * Identifier will be forwarded with received location
         */
        val identifier: String = "",
        /**
         * minimal time between 2 location request in milliseconds
         */
        val minTimeMS: Long = 0L,
        /**
         * minimal distance between 2 location request in meters
         */
        val minDistanceM: Long = 0,
        /**
         * Indicates the desired horizontal accuracy (latitude and longitude)
         *
         * * 0 -> no accuracy
         * * 1 -> LOW (greater than 500 meters)
         * * 2 -> MEDIUM (between 100 and 500 meters)
         * * 3 -> HIGH (less than 100 meters)
         */
        var horizontalAccuracy: Int = 0,
        /**
         * Indicates the desired vertical accuracy (altitude)
         *
         * * 0 -> no accuracy
         * * 1 -> LOW (greater than 500 meters)
         * * 2 -> MEDIUM (between 100 and 500 meters)
         * * 3 -> HIGH (less than 100 meters)
         */
        val verticalAccuracy: Int = 0,
        /**
         * Indicates the desired speed accuracy
         *
         * * 0 -> no accuracy
         * * 1 -> LOW (greater than 500 meters)
         * * 3 -> HIGH (less than 100 meters)
         */
        val speedAccuracy: Int = 0,
        /**
         * Indicates the desired bearing accuracy
         *
         * * 0 -> no accuracy
         * * 1 -> LOW (greater than 500 meters)
         * * 3 -> HIGH (less than 100 meters)
         */
        val bearingAccuracy: Int = 0,
        /**
         * Indicates the desired maximum power level
         *
         * * 0 -> any
         * * 1 -> LOW  low power requirement
         * * 2 -> MEDIUM  medium power requirement
         * * 3 -> HIGH  high power requirement
         */
        val powerRequirement: Int = 0,
        /**
         * Indicates whether the provider must provide altitude information
         */
        val altitudeRequired: Boolean = false,
        /**
         * Indicates whether the provider must provide bearing information
         */
        val bearingRequired: Boolean = false,
        /**
         * Indicates whether the provider must provide speed information
         */
        val speedRequired: Boolean = false,
        /**
         * Indicates whether the provider is allowed to incur monetary cost
         */
        val costAllowed: Boolean = false,
)
```

### 6.2 location callback

Recieve location updates

```js
loc.callback(function(resp){console.log(resp)},function(err){console.log("error:xxx ->" + err)})
```

Location response object
```kotlin
data class LocationResponse(
        var identifier: String = "",
        val state: State,
        val provider: String = "unknown",
        val accuracy: Float = 0.0f,
        val lat: Double = 0.0,
        val lon: Double = 0.0,
        val altitude: Double = 0.0,
        val dt: Long = System.currentTimeMillis(), //in case state of NEW_LOCATION is datetime generated by location provider 
        val bearing: Float = 0.0f,
        val speed: Float = 0.0f,
        val wifi: String = "", // name of currently connected wifi
        val battery: Int = -1,// mobile battery state (0-100)
 		val distanceM: Long = -1 //distance in meters from last position
		) { 

    enum class State {
        IDLE, // startup point
        NEW_LOCATION, // new location
        PROVIDER_ENABLED, // user enable location provider (GPS/NETWORK/OTHER)
        PROVIDER_DISABLED // user DISABLE location provider (GPS/NETWORK/OTHER)
    }
}
```

### 6.3 Stop

Stop location listener and unregister callback 

```js
loc.stop(function(resp){console.log(resp)},function(err){console.log("error:xxx ->" + err)})
```

### 6.4 Location query

Retrive location possitions by 
* identifier -> unique id used in start call ``loc.start({identifier: "xyz"}, {}, {})``
* offset -> query offset of location results
* limit -> max query length

Response is array of response objests same as ``loc.callback`` response

```js
loc.query(identifier: "xyz",
	offset: 0, 
	limit: 1000,
    	function(resp){console.log(resp)},
    	function(err){console.log("error:xxx ->" + err)})
```

### 6.5 Last location

Response is last hnown location

```js
loc.last(
    	function(resp){console.log(resp)}, // succes
    	function(err){console.log("error:xxx ->" + err)}) // no location in database
```

### 6.6 CleanUP

Remove all stored locations by identifier.

```js
loc.clear(identifier: "xyz",
	function(resp){console.log(resp)},
    	function(err){console.log("error:xxx ->" + err)})
```