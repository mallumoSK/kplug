/*global cordova, module*/

module.exports.greet = function (json, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "SttPlugFG", "greet", [json]);
}

module.exports.start = function (data, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "SttPlugFG", "start", [data]);
}

module.exports.stop = function () {
        cordova.exec(function(resp){}, function(err){}, "SttPlugFG", "stop", [0]);
}

module.exports.text = function (text, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "SttPlugFG", "text", [text]);
}

module.exports.color = function (button, animation, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "SttPlugFG", "color", [button, animation]);
}
