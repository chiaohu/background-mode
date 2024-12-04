/*
    Copyright 2013-2017 appPlant GmbH

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
    OF ANY KIND, either express or implied.  See the License for
    the specific language governing permissions and limitations
    under the License.
*/

var exec = require('cordova/exec'),
    channel = require('cordova/channel');

/**
 * Activates the background mode. When activated the application
 * will be prevented from going to sleep while in background
 * for the next time.
 *
 * @return [ Void ]
 */
exports.enable = function () {
    if (this.isEnabled())
        return;

    var fn = function () {
        exports._isEnabled = true;
        exports.fireEvent('enable');
    };

    cordova.exec(fn, null, 'BackgroundMode', 'enable', []);
};

/**
 * Deactivates the background mode. When deactivated the application
 * will not stay awake while in background.
 *
 * @return [ Void ]
 */
exports.disable = function () {
    if (!this.isEnabled())
        return;

    var fn = function () {
        exports._isEnabled = false;
        exports.fireEvent('disable');
    };

    cordova.exec(fn, null, 'BackgroundMode', 'disable', []);
};

/**
 * Enable or disable the background mode.
 *
 * @param [ Bool ] enable The status to set for.
 *
 * @return [ Void ]
 */
exports.setEnabled = function (enable) {
    if (enable) {
        this.enable();
    } else {
        this.disable();
    }
};

/**
 * List of all available options with their default value.
 *
 * @return [ Object ]
 */
exports.getDefaults = function () {
    return this._defaults;
};

/**
 * The actual applied settings.
 *
 * @return [ Object ]
 */
exports.getSettings = function () {
    return this._settings || {};
};

/**
 * Overwrite the default settings.
 *
 * @param [ Object ] overrides Dict of options to be overridden.
 *
 * @return [ Void ]
 */
exports.setDefaults = function (overrides, fn) {
    var defaults = this.getDefaults();

    for (var key in defaults) {
        if (overrides.hasOwnProperty(key)) {
            defaults[key] = overrides[key];
        }
    }

    console.log('New defaults:', defaults);

    if (this._isAndroid) {
        cordova.exec((fn || function () { }), null, 'BackgroundMode', 'configure', [defaults, false]);
    }
};

/**
 * Configures the notification settings for Android.
 * Will be merged with the defaults.
 *
 * @param [ Object ] options Dict of options to be overridden.
 *
 * @return [ Void ]
 */
exports.configure = function (options) {
    var settings = this.getSettings(),
        defaults = this.getDefaults();

    if (!this._isAndroid) return;

    if (!this._isActive) {
        console.log('BackgroundMode is not active, skipped...');
        return;
    }

    this._mergeObjects(options, settings);
    this._mergeObjects(options, defaults);
    this._settings = options;

    console.log('Configuring settings:', options);

    cordova.exec(null, null, 'BackgroundMode', 'configure', [options, true]);
};

/**
 * Enable GPS-tracking in background (Android).
 *
 * @return [ Void ]
 */
exports.disableWebViewOptimizations = function () {
    if (this._isAndroid) {
        cordova.exec(null, null, 'BackgroundModeExt', 'webview', []);
    }
};

/**
 * Disables battery optimization mode for the app.
 *
 * @return [ Void ]
 */
exports.disableBatteryOptimizations = function () {
    if (this._isAndroid) {
        cordova.exec(null, null, 'BackgroundModeExt', 'battery', []);
    }
};

/**
 * Move app to background (Android only).
 *
 * @return [ Void ]
 */
exports.moveToBackground = function () {
    if (this._isAndroid) {
        cordova.exec(null, null, 'BackgroundModeExt', 'background', []);
    }
};

/**
 * Move app to foreground when in background (Android only).
 *
 * @return [ Void ]
 */
exports.moveToForeground = function () {
    if (this.isActive() && this._isAndroid) {
        cordova.exec(null, null, 'BackgroundModeExt', 'foreground', []);
    }
};

/**
 * If the screen is off.
 *
 * @param [ Function ] fn Callback function to invoke with boolean arg.
 *
 * @return [ Void ]
 */
exports.isScreenOff = function (fn) {
    if (this._isAndroid) {
        cordova.exec(fn, null, 'BackgroundModeExt', 'dimmed', []);
    } else {
        fn(undefined);
    }
};

/**
 * If the mode is enabled or disabled.
 *
 * @return [ Boolean ]
 */
exports.isEnabled = function () {
    return this._isEnabled !== false;
};

/**
 * If the mode is active.
 *
 * @return [ Boolean ]
 */
exports.isActive = function () {
    return this._isActive !== false;
};

exports._listener = {};

/**
 * Fire event with given arguments.
 *
 * @param [ String ] event The event's name.
 * @param [ Array<Object> ] The callback's arguments.
 *
 * @return [ Void ]
 */
exports.fireEvent = function (event) {
    var args = Array.apply(null, arguments).slice(1),
        listener = this._listener[event];

    if (!listener)
        return;

    for (var i = 0; i < listener.length; i++) {
        var fn = listener[i][0],
            scope = listener[i][1];

        fn.apply(scope, args);
    }
};

/**
 * Register callback for given event.
 *
 * @param [ String ] event The event's name.
 * @param [ Function ] callback The function to be exec as callback.
 * @param [ Object ] scope The callback function's scope.
 *
 * @return [ Void ]
 */
exports.on = function (event, callback, scope) {
    if (typeof callback !== "function")
        return;

    if (!this._listener[event]) {
        this._listener[event] = [];
    }

    var item = [callback, scope || window];

    this._listener[event].push(item);
};

/**
 * Default values of all available options.
 */
exports._defaults = {
    title: 'App is running in background',
    text: 'Doing heavy tasks.',
    bigText: false,
    resume: true,
    silent: false,
    hidden: true,
    color: undefined,
    icon: 'icon'
};

/**
 * Merge settings with default values.
 *
 * @param [ Object ] options The custom options.
 * @param [ Object ] toMergeIn The options to merge in.
 *
 * @return [ Object ] Default values merged with custom values.
 */
exports._mergeObjects = function (options, toMergeIn) {
    for (var key in toMergeIn) {
        if (!options.hasOwnProperty(key)) {
            options[key] = toMergeIn[key];
            continue;
        }
    }

    return options;
};

/**
 * Initialize the plugin.
 *
 * @return [ Void ]
 */
exports._pluginInitialize = function () {
    this._isAndroid = device.platform.match(/^android|amazon/i) !== null;
    this.setDefaults({});

    if (device.platform === 'browser') {
        this.enable();
        this._isEnabled = true;
    }

    this._isActive = this._isActive || device.platform === 'browser';
};

channel.deviceready.subscribe(function () {
    if (exports.isEnabled()) {
        exports.fireEvent('enable');
    }

    if (exports.isActive()) {
        exports.fireEvent('activate');
    }
});