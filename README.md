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
	autoContinue: true	
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

### 4.4 enable single permission

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

### 4.5 multiple permissions

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

### 4.6 enable all project permissions

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
