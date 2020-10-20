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
```

## Cordova requirments
* enabled kotlin v1.4.10
* cordove min. version 9.0.0

In config:
```xml
<preference name="GradlePluginKotlinEnabled" value="true" />
<preference name="GradlePluginKotlinCodeStyle" value="official" />
<preference name="GradlePluginKotlinVersion" value="1.4.10" />
```

##### chrome inspect window 
chrome://inspect/#devices

## Test of working
```
kplug.greet("World",function(resp){console.log(resp)},function(err){console.log("error:xxx ->" + err)})
```

## 1. Speech to text
### 1.1. START
```
kplug.stt({
	preferOffline: false,
	maxResults: 10	
	},function(resp){console.log(resp)},function(err){console.log("error:xxx ->" + err)})
```
### 1.2. STOP
```
kplug.sttStop(function(resp){console.log(resp)},function(err){console.log("error:xxx ->" + err)})
```
### 1.3. STOP IMMEDIATELY
```
kplug.sttStopForce(function(resp){console.log(resp)},function(err){console.log("error:xxx ->" + err)})
```

## 2. Notification scheduler
### 2.1 Schedule
```
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

### 2.2 Stop schedule
```
kplug.scheduleCancel("some-unique-identifier",function(resp){console.log(resp)},function(err){console.log("error:xxx ->" + err)})
```