(function(scope) {

    function TagDefBuilder(def) {
        return Utils.extend({
            allowedChildren: null,
            preprocess: function(node, attrs) {

            },
            render: function(node, attrs) {
                throw "handler not implemented";
            }
        },def);
    }

    function AttributeDefBuilder(def) {
        return Utils.extend({
            preprocess: function(node, attrs) {

            },
            render: function(node, attrs) {
                throw "handler not implemented";
            }
        },def);
    }

    function Webi(opts) {
        this._opts = Utils.extend({}, opts);
        this._tags = {};
        this._attributes = {};
        this._compiler = new Compiler(this);
    }


    Webi.prototype = {
        _opts: null,
        /**
         * Map of tag definitions
         */
        _tags: null,
        /**
         * Map of attribute definitions by tag name. * = "any"
         */
        _attributes: null,

        /**
         * @type Compiler
         */
        _compiler: null,

        /**
         * Add tag handler
         * @param string localName the tag name
         * @param Object tagDef a function which provides the tag. The function gets a map of attributes as an argument
         * @param string [namespace]
         */
        addTag: function (tagName, tagDef) {
            this._tags[tagName.toLowerCase()] = TagDefBuilder(tagDef);
        },
        getTagHandler: function(tagName) {
            return this._tags[tagName.toLowerCase()];
        },
        /**
         * Adds attribute handler
         * @param string name
         * @param Object attributeDef
         * @param array [tags]
         */
        addAttribute: function (attribute, attributeDef, tags) {
            var me = this;
            if (!tags) {
                tags = ['*'];
            }
            attribute = attribute.toLowerCase();

            var attrDef = AttributeDefBuilder(attributeDef);

            Utils.each(tags,function(tag) {
                tag = tag.toLowerCase();
                if (!me._attributes[tag]) {
                    me._attributes[tag] = {};
                }

                me._attributes[tag][attribute] = attrDef;
            });
        },
        getAttributeHandler: function(tag , attribute) {
            tag = tag.toLowerCase();
            attribute = attribute.toLowerCase();

            if (this._attributes[tag] &&
                this._attributes[tag][attribute]) {
                return this._attributes[tag][attribute];
            }

            if (this._attributes['*'] &&
                this._attributes['*'][attribute]) {
                return this._attributes['*'][attribute];
            }

            return null;
        },
        /**
         * Compiles a template string into a Template instance
         * @param src
         * @returns {Template}
         */
        compile: function(src) {
            return this._compiler.compile(src);
        },
        render: function(tmplSrc, data) {
            this.compile(tmplSrc).render(data);
        }
    };

    scope.Webi = Webi;
})(this);

