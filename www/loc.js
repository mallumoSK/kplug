/*global cordova, module*/


module.exports.locStart = function (json, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "LocationPlug", "start", [json]);
}

module.exports.locStop = function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "LocationPlug", "stop", [0]);
}

module.exports.locCallback = function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "LocationPlug", "callback", [0]);
}

module.exports.locQuery = function (identifier, offset, limit, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "LocationPlug", "query", [identifier, offset, limit]);
}

module.exports.locClear = function (identifier, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "LocationPlug", "clear", [identifier]);
}
