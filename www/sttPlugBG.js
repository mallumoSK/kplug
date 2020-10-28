/*global cordova, module*/

module.exports.greet = function (json, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "SttPlugBG", "greet", [json]);
}

module.exports.start = function (data, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "SttPlugBG", "start", [data]);
}

module.exports.stop = function () {
        cordova.exec(function(resp){}, function(err){}, "SttPlugBG", "stop", [0]);
}

module.exports.stopForce = function () {
        cordova.exec(function(resp){}, function(err){}, "SttPlugBG", "stopForce", [0]);
}
