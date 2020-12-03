/*global cordova, module*/


module.exports.start = function (json, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "LocationPlug", "start", [json]);
}

module.exports.stop = function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "LocationPlug", "stop", [0]);
}

module.exports.callback = function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "LocationPlug", "callback", [0]);
}

module.exports.query = function (identifier, offset, limit, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "LocationPlug", "query", [identifier, offset, limit]);
}

module.exports.clear = function (identifier, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "LocationPlug", "clear", [identifier]);
}

module.exports.last = function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "LocationPlug", "last", [0]);
}
