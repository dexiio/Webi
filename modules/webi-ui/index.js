var container = document.getElementById('container');

var webi = new Webi();

webi.addAttribute('each', {
    scoped: true,
    bind: function bindEach(placeHolder, scope, node) {
        var model = node.getAttribute('each');
        var it = node.getAttribute('as');
        if (!it) {
            it = 'it';
        }
        node.removeAttribute('each');
        node.removeAttribute('as');


        console.log('placeholder', placeHolder);

        var template = new Compiler(webi).compile(node.outerHTML);

        var childDoms = [];

        var watchEntries = function (entries) {
            Utils.each(childDoms, function (child) {
                parentNode.removeChild(child);
            });
            childDoms = [];

            Utils.each(entries, function (entry) {
                var o = new Observable({}, scope);
                o.$set(it, entry);

                var childDom = template.render(o);
                childDoms.push(childDom);
                Utils.insertNodeBefore(childDom, placeHolder);
            });
        };

        watchEntries(scope.$get(model,[]));

        scope.$watch(model, watchEntries);
    }
});

var compiler = new Compiler(webi);
var parent = container.parentNode;
var compiled = compiler.compile(container.innerHTML);
parent.removeChild(container);

console.log('Compiled', compiled);

var o = new Observable({
    formTitle: 'Form Title',
    formName: 'Form name',
    other: {
        test: 'test 1',
        other: 'test 2'
    },
    labelTitle: 'Label title',
    nested: {testing: {object: 1}},
    entries: [
        {
            className: 'entry1',
            name: 'Entry 1'
        },
        {
            className: 'entry2',
            name: 'Entry 2'
        },
        {
            className: 'entry3',
            name: 'Entry 3'
        }
    ]
});

var dom = compiled.render(o);

parent.appendChild(dom);