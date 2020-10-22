/*global cordova, module*/


module.exports.schedule = function (data, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "KPlug", "schedule", [data]);
}

module.exports.greet = function (json, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "KPlug", "greet", [json]);
}

module.exports.scheduleCancel = function (id, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "KPlug", "scheduleCancel", [id]);
}

module.exports.stt = function (data, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "KPlug", "stt", [data]);
}

module.exports.sttStop = function () {
        cordova.exec(function(resp){}, function(err){}, "KPlug", "sttStop", [0]);
}

module.exports.sttStopForce = function () {
        cordova.exec(function(resp){}, function(err){}, "KPlug", "sttStopForce", [0]);
}
