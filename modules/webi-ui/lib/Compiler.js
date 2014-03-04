(function (scope) {

    function Compiler(webi) {
        this._webi = webi;
        if (!webi) {
            throw "Missing argument: webi";
        }
    }

    function defaultTextExpression(exprRegex, text) {
        var expression = '"' + text + '"';
        return expression.replace(exprRegex,function (match, expr) {
            return '" + scope.$get(\'' + expr + '\',\'\') + "';
        }).replace(/""\s*\+\s*/, '')
            .replace(/\s*\+\s*""/, '')
            .replace(/\n+/g, '\\n');
    }

    /**
     *
     * @param node
     * @param attrStrs
     * @param [bodyStr]
     * @returns {string}
     */
    function defaultTagExpression(node, attrStrs, bodyStr) {
        var out = '"<' + node.tagName.toLowerCase();

        if (attrStrs.length > 0) {
            out += ' " + ' + attrStrs.join(' + " "\n+ ') + ' + "';
        }

        if (bodyStr) {
            out += '>"\n + ' + bodyStr + ' +\n"</' + node.tagName.toLowerCase() + '>"';
        } else {
            out += '/>"\n';
        }

        return out;
    }

    function defaultAttributeExpression(node, name, val) {
        return '"' + name + '=\\"" + ' + val + ' + "\\""';
    }

    Compiler.prototype = {
        constructor: Compiler,
        /**
         * @type Webi
         */
        _webi: null,

        /**
         * Internal regex patterns
         */
        _patterns: {
            inline: /\{\{([a-z_][a-z0-9_\-\.]*)\}\}/gi
        },
        /**
         * Compile source into Template. If source has been precompiled and is available that will be used
         * @param string src
         * @returns {Template}
         */
        compile: function (src) {
            var container = Utils.element(src);
            var result = this._compileElement(container, container);

            var jsFunc = 'function template(scope) { return ' + result.expression.replace(/"\s*\+\s*"/g, '') + ';}';
            return new Template(jsFunc, result.bindings);
        },

        _pattern: function (id) {
            this._patterns[id].lastIndex = 0;
            return this._patterns[id];
        },
        _readExpression: function (str) {
            var res,
                rx = this._pattern('inline'),
                out = [];


            do {
                res = rx.exec(str)
                if (res) {
                    out.push(res[1]);
                }
            } while (res !== null);

            return out;
        },

        _compileElement: function (node, root) {
            var i,
                bindings = [],
                me = this;

            var nodePath = Utils.nodePath(node, root);

            var tagHandler = this._webi.getTagHandler(node.tagName);

            var attrs = {};
            for (i = 0; i < node.attributes.length; i++) {
                var attr = node.attributes.item(i);
                attrs[attr.name] = attr.value;
            }

            var $attrs = new Observable(attrs);
            Utils.instrument(node, 'setAttribute', function (name, value) {
                $attrs.$set(name, value);
            });

            if (tagHandler) {
                tagHandler.preprocess(node, $attrs);

                bindings.push({
                    element: nodePath,
                    handler: tagHandler
                });
            }

            var attrStrs = [],
                bodyParts = [],
                scoped = false,
                scopedHandler;

            if (tagHandler && tagHandler.scoped) {
                scoped = true;
                scopedHandler = tagHandler;
            }

            if (!scoped) {
                $attrs.$each(function (key, val) {
                    if (scoped) {
                        return;
                    }

                    var attrHandler = me._webi.getAttributeHandler(node.tagName, key);

                    if (attrHandler) {
                        attrHandler.preprocess(node, $attrs);
                    }

                    var valExpr = defaultTextExpression(me._pattern('inline'), val);

                    if (me._pattern('inline').test(val)) {
                        var variables = me._readExpression(val);
                        bindings.push({
                            element: nodePath,
                            expression: valExpr,
                            attribute: key,
                            variables: variables,
                            handler: attrHandler
                        });
                    } else if (attrHandler) {
                        bindings.push({
                            element: nodePath,
                            attribute: key,
                            handler: attrHandler
                        });
                    }

                    if (attrHandler && attrHandler.expression) {
                        attrStrs.push(attrHandler.expression(node, key, valExpr));
                    } else {
                        attrStrs.push(defaultAttributeExpression(node, key, valExpr));
                    }

                    if (attrHandler && attrHandler.scoped && !scopedHandler) {
                        scoped = true;
                        scopedHandler = attrHandler;
                    }
                });
            }

            if (scoped) {
                var parent = node.parentNode;
                var commentNode = document.createComment('scoped');
                parent.insertBefore(commentNode, node);
                parent.removeChild(node);
                return {
                    bindings: [
                        {
                            element: Utils.nodePath(commentNode, root),
                            handler: scopedHandler,
                            node: node
                        }
                    ],
                    expression: '"<!-- ' + commentNode.nodeValue + ' -->"'
                };
            }

            for (i = 0; i < node.childNodes.length; i++) {
                var child = node.childNodes[i];

                var result = null;
                switch (child.nodeType) {
                    case 1: //Element
                        result = this._compileElement(child, root);
                        break;
                    case 3: //Text
                        result = this._compileText(child, root);
                        break;
                }

                if (result) {
                    Utils.addAll(bindings, result.bindings);
                    if (result.expression) {
                        bodyParts.push(result.expression);
                    }
                }
            }

            var bodyExpr = '';

            if (bodyParts.length > 0) {
                bodyExpr = bodyParts.join(' + ');
            }

            var expression = '';

            if (tagHandler && tagHandler.expression) {
                expression = tagHandler.expression(node, attrStrs, bodyExpr)
            } else {
                expression = defaultTagExpression(node, attrStrs, bodyExpr);
            }

            return {
                bindings: bindings,
                expression: expression
            };
        },
        _compileText: function (node, root) {
            var nodePath = Utils.nodePath(node, root);
            var text = node.nodeValue;
            var bindings = [];
            var textExpr = '';
            if (text.length > 0) {
                textExpr = defaultTextExpression(this._pattern('inline'), text);
                if (this._pattern('inline').test(text)) {
                    var variables = this._readExpression(text);
                    bindings.push({
                        element: nodePath,
                        expression: textExpr,
                        attr: null,
                        variables: variables
                    });
                }
            }

            return {
                bindings: bindings,
                expression: textExpr
            };
        }
    };

    scope.Compiler = Compiler;
})(this);