var Template = require('./Template.js'),
    View = require('./View.js');

module.exports = {
    Template: Template,
    View: View,
    /**
     * Compile target template into view
     * @param string|HTMLElement compileTarget a raw string template or html element with template inside.
     */
    compile: function(compileTarget) {
        return 'test';
    },
    /**
     * Add tag handler
     * @param localName
     * @param [namespace]
     */
    addTag:function(localName, namespace) {

    }
};


