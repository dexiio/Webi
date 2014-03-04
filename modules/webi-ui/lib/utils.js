(function (scope) {
    scope.Utils = {
        addAll: function (arr) {
            for (var i = 1; i < arguments.length; i++) {
                var arg = arguments[i];
                if (Utils.isArray(arg)) {
                    this.each(arg, function (val) {
                        arr.push(val);
                    });
                } else {
                    arr.push(arg);
                }
            }
            return arr;
        },
        extend: function (obj) {
            for (var i = 1; i < arguments.length; i++) {
                var arg = arguments[i];
                if (typeof arg === 'object') {
                    this.each(arg, function (key, val) {
                        obj[key] = val;
                    });
                } else {
                    if (arg) {
                        throw ("Invalid argument: " + arg);
                    }
                }
            }
            return obj;
        },
        each: function (obj, callback) {

            if (Utils.isArray(obj)) {
                for (var i = 0; i < obj.length; i++) {
                    callback(obj[i], i);
                }
                return;
            }

            for (var key in obj) {
                if (obj.hasOwnProperty(key)) {
                    callback(key, obj[key]);
                }
            }
        },
        nodeIndex: function (node) {
            var parent = node.parentNode;
            if (!parent) {
                return -1;
            }

            for (var i = 0; i < parent.childNodes.length; i++) {
                var child = parent.childNodes[i];
                if (child === node) {
                    return i;
                }
            }

            return -1;
        },
        nodePath: function (node, until) {
            if (!until) {
                until = document.documentElement;
            }
            if (!node || node === until) {
                return "";
            }

            var thisPath = "";

            switch(node.nodeType) {
                case 1:
                    thisPath = node.tagName.toLowerCase();
                    break;
                case 3:
                    thisPath = '#text';
                    break;
                case 8:
                    thisPath = '#comment';
                    break;
            }

            var ix = this.nodeIndex(node);

            if (ix > -1) {
                thisPath += '[' + ix + ']';
            } else {
                return '';
            }

            var parentPath = this.nodePath(node.parentNode, until);
            if (parentPath) {
                parentPath += "/"
            }

            return parentPath + thisPath;
        },
        findNode: function (container, path) {
            if (!path.trim()) {
                return container;
            }
            var parts = path.trim().split(/\//g);
            var node = container;
            this.each(parts, function (part) {
                var res = /([^\[\]]+)(?:\[([0-9]+)\])?/.exec(part);
                if (!res) {
                    throw "Part did not match pattern: " + part + " in " + path;
                }
                var tagName = res[1].toLowerCase();
                var ix = parseInt(res[2], 10);
                if (!ix || isNaN(ix)) {
                    ix = 0;
                }

                var child = node.childNodes[ix];
                if (!child) {
                    console.error(container,child);
                    throw "Child not found at index: " + ix + " in path: " + path + ' (' + part + ')';

                }

                switch(tagName) {
                    case '#text':
                        if (child.nodeType !== 3) {
                            throw "Text node not found at index: " + ix + " in path: " + path + ' (' + part + ')';
                        }
                        break;
                    case '#comment':
                        if (child.nodeType !== 8) {
                            throw "Comment node not found at index: " + ix + " in path: " + path + ' (' + part + ')';
                        }
                        break;
                    default:
                        if (child.nodeType !== 1) {
                            throw "Element not found at index: " + ix + " in path: " + path + ' (' + part + ')';
                        }
                        if (child.tagName.toLowerCase() !== tagName) {
                            throw "Element not found at index: " + ix + " in path: " + path + ' (' + part + ')';
                        }
                        break;
                }

                node = child;
            });

            return node;
        },
        instrument: function (obj, methodName, afterMethod) {
            var oldMethod = obj[methodName];
            obj[methodName] = function() {
                oldMethod.apply(this,arguments);
                afterMethod.apply(this,arguments);
            };
        },
        element: function(src) {
            var container = document.createElement('div');
            container.innerHTML = src;
            if (container.children.length == 1) {
                return container.children[0];
            }
            return container;
        },
        fun: function(src) {
            return new Function("return "+src).call();
        },
        insertNodeBefore: function(node, refNode) {
            refNode.parentNode.insertBefore(node, refNode);
        },
        insertNodeAfter: function(node, refNode) {
            var parent = refNode.parentNode;
            if (refNode.nextSibling) {
                parent.insertBefore(node, refNode.nextSibling);
            } else {
                parent.appendChild(node);
            }

        },
        isArray: function(val) {
            return val instanceof Array;
        }
    };
})(this)