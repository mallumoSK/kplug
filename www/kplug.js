/*global cordova, module*/


module.exports.greet = function (json, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "KPlug", "greet", [json]);
}

module.exports.schedule = function (data, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "KPlug", "schedule", [data]);
}

module.exports.scheduleCancel = function (id, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "KPlug", "scheduleCancel", [id]);
}
