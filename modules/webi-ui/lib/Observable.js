(function (scope) {

    function ObservableArray(arr) {
        Array.prototype.constructor.apply(this, arguments);

        Object.defineProperty(this, '_listeners', {
            enumerable: false,
            value: []
        });

        if (arr && Utils.isArray(arr)) {
            var me = this;
            arr.forEach(function (entry) {
                me.push(entry);
            });
        }
    }

    ObservableArray.prototype = new Array;
    ObservableArray.prototype.constructor = ObservableArray;

    ObservableArray.prototype.$watch = function (listener) {
        this._listeners.push(listener);
    };

    ['push', 'pop', 'splice', 'concat', 'join', 'reduce', 'reverse', 'shift', 'unshift', 'sort'].forEach(function (method) {
        var oldMethod = ObservableArray.prototype[method];
        ObservableArray.prototype[method] = function () {
            var me = this;
            oldMethod.apply(this, arguments);

            this._listeners.forEach(function (listener) {
                listener(me, me);
            });
        };
    });


    function Observable(props, parentObservable) {
        var me = this,
            listeners = {},
            data = {};

        function addProperty(key, val) {

            Object.defineProperty(me, key, {
                enumerable: true,
                get: function () {
                    me.$get(key);
                },
                set: function (newVal) {
                    me.$set(key, newVal);
                }
            });


        }

        function triggerChange(key, val, oldVal) {
            if (listeners[key]) {
                Utils.each(listeners[key], function (listener) {
                    listener(key, val, oldVal);
                });
            }

            if (listeners['*']) {
                Utils.each(listeners['*'], function (listener) {
                    listener(key, val, oldVal);
                });
            }
        }

        /**
         * Get value - also available through normal property getter.
         * @param string key
         * @returns {*}
         */
        this.$get = function (expr, defaultVal) {
            var parts = expr.split(/\./g);
            var p = data;
            Utils.each(parts, function (part) {
                if (!p) {
                    return;
                }
                if (Utils.isArray(p) && !/^[0-9]+$/.test(part)) {
                    //If it's an array - find the first with an existing property of that name
                    for (var i = 0; i < p.length; i++) {
                        var subP = p[i];
                        if (subP[part] !== undefined) {
                            p = subP[part];
                            return;
                        }
                    }

                    p = p[0]; //Fall back to the first entry in the array
                    if (!p) {
                        return;
                    }
                }

                if (p instanceof Observable) {
                    p = p.$get(part);
                } else {
                    p = p[part];
                }
            });

            if (p === undefined) {
                return defaultVal;
            }
            return p;
        };

        this.$pathSet = function (expr, value) {
            var parts = expr.split(/\./g);
            var p = data;
            var lastPart, lastP;
            Utils.each(parts, function (part) {
                if (Utils.isArray(p) && !/^[0-9]+$/.test(part)) {
                    //If it's an array - find the first with an existing property of that name
                    for (var i = 0; i < p.length; i++) {
                        var subP = p[i];
                        if (subP[part] !== undefined) {
                            lastP = p;
                            lastPart = part;
                            p = subP[part];
                            return;
                        }
                    }

                    if (p.length === 0) {
                        p.push({});
                    }

                    p = p[0]; //Fall back to the first entry in the array
                }

                if (!p[part]) {
                    p[part] = {};
                }

                lastP = p;
                lastPart = part;

                p = p[part];
            });

            if (lastP instanceof Observable) {
                lastP.$set(lastPart, value);
            } else {
                lastP[lastPart] = value;
            }

            return this;
        };

        /**
         * Set value - also available through normal property setter.
         * @param string key
         * @param mixed val
         */
        this.$set = function (key, newVal) {
            if (!this.hasOwnProperty(key)) {
                addProperty(key, newVal);
            }

            var oldVal = data[key];

            if (typeof oldVal !== 'object'
                && typeof newVal === 'object') {
                if (Utils.isArray(newVal)) {
                    newVal = new ObservableArray(newVal);
                    newVal.$watch(function (val, old) {
                        triggerChange(key, val, old);
                    });
                } else {
                    newVal = new Observable(newVal, this);
                }
            }

            data[key] = newVal;
            if (oldVal !== newVal) {
                triggerChange(key, newVal, oldVal);
            }
        };

        /**
         * Watch property.
         * @param string|function key property name or optionally a listener which will receive all changes
         * @param function [listener] change listener - gets called with key,newVal,oldVal
         * @returns {Function}
         */
        this.$watch = function (key, listener) {
            var me = this;
            if (typeof key === 'function') {
                listener = key;
                key = '*'
            }

            if (Utils.isArray(key)) {
                var unwatchers = [];
                Utils.each(key, function (k) {
                    unwatchers.push(me.$watch(k, listener));
                });

                return function unwatchMultiple() {
                    Utils.each(unwatchers, function (unwatch) {
                        unwatch();
                    });
                };
            }

            if (!listeners[key]) {
                listeners[key] = [];
            }

            listeners[key].push(listener);

            return function unwatch() {
                var ix = listeners[key].indexOf(listener);
                if (ix > -1) {
                    listeners[key].splice(ix, 1);
                }
            };
        };

        this.$each = function (iterator) {
            Utils.each(data, function (key, val) {
                iterator(key, val);
            });
        };

        this.$toObject = function () {
            return Utils.extend({}, data);
        };

        this.$toJSON = function () {
            return JSON.stringify(this.$toObject());
        };

        Utils.each(props, function (key, val) {
            me.$set(key, val);
        });
    }

    scope.ObservableArray = ObservableArray;
    scope.Observable = Observable;
})(this);