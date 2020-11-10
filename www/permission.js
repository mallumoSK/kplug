/*global cordova, module*/


module.exports.enableSingle = function (singlePermission, successCallback) {
        cordova.exec(successCallback, {}, "Permission", "enableSingle", [singlePermission]);
}

module.exports.enableMultiple = function (multiplePermissions, successCallback) {
        cordova.exec(successCallback, {}, "Permission", "enableMultiple", multiplePermissions);
}

module.exports.enableAll = function (successCallback) {
        cordova.exec(successCallback, {}, "Permission", "enableAll", []);
}

module.exports.listAll = function (successCallback) {
        cordova.exec(successCallback, {}, "Permission", "listAll", []);
}

module.exports.listEnabled = function (successCallback) {
        cordova.exec(successCallback, {}, "Permission", "listEnabled", []);
}

module.exports.listDisabled = function (successCallback) {
        cordova.exec(successCallback, {}, "Permission", "listDisabled", []);
}
