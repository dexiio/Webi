(function (scope) {
    /**
     * Template prototype that wraps a compiled template
     * @constructor
     */
    function Template(tmplFunction, bindings) {
        this._bindings = bindings;
        this._tmplFunction = Utils.fun(tmplFunction);
    }

    Template.prototype = {
        /**
         * Element bindings
         */
        _bindings: null,

        /**
         * @type Function
         */
        _tmplFunction: null,


        /**
         *
         * @param {Observable} scope
         * @return {HTMLElement}
         */
        render: function (scope) {
            var rendered = this._tmplFunction.call(this, scope);
            var domOut = Utils.element(rendered);

            Utils.each(this._bindings, function renderBinding(binding) {
                var node = Utils.findNode(domOut, binding.element);
                if (binding.expression) {
                    var exprFun = Utils.fun('function(scope){return ' + binding.expression + ';}');

                    scope.$watch(binding.variables, function () {
                        var newVal = exprFun(scope);
                        if (binding.attribute) {
                            node.setAttribute(binding.attribute, newVal);
                        } else {
                            node.nodeValue = newVal;
                        }
                    });
                }

                if (binding.handler && binding.handler.bind) {
                    binding.handler.bind(node, scope, binding.node);
                }
            });

            return domOut;
        }

    };
    scope.Template = Template;
})(this);