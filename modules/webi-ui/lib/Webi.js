var Template = require('./Template.js'),
    View = require('./View.js'),
    _ = require('lodash'),
    HTML5 = require('html5');


var INLINE_REGEX = /\{\{[a-z_][a-z0-9_\-\.]*\}\}/ig

function Webi(opts) {
    this._opts = _.extend({}, opts);
    this._tags = {};
}

function compileElement(node) {
    var out = [];
    for(var ix = 0; i < node.childNodes.length; i++) {
        var child = node.childNodes[i];
        switch(child.nodeType) {
            case 1: //Element
                //Get attributes
                _.concat(out,compileElement(child));
                break;
            case 3: //Text
                if (INLINE_REGEX.test(child.nodeValue)) {
                    out.push({
                        node: child,
                        attr: null,
                        variables:['some','vars']
                    });
                }
                break;
        }
    }

    return out;
}



Webi.prototype = {
    _opts:null,
    _tags:null,
    /**
     * Compile target template into view
     * @param string templateSource a raw string template.
     */
    compile: function(templateSource) {
        var parser = new HTML5.Parser();
        parser.parse(templateSource);
        var root = parser.fragment;

        for(var ix = 0; i < root.childNodes.length; i++) {
            root.childNodes[]
            switch (child.nodeType)
        }

        return new Template(root);
    },
    /**
     * Add tag handler
     * @param string localName the tag name
     * @param function tagProvider a function which provides the tag. The function gets a map of attributes as an argument
     * @param string [namespace]
     */
    addTag: function(localName, tagProvider, namespace) {
        var tag = namespace ? namespace + ":" + localName : localName;
        this._tags[tag] = tagProvider;
    }
};

module.exports = Webi;

