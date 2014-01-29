'use strict';

var Webi = require('../lib/Webi.js');

/*
  ======== A Handy Little Nodeunit Reference ========
  https://github.com/caolan/nodeunit

  Test methods:
    test.expect(numAssertions)
    test.done()
  Test assertions:
    test.ok(value, [message])
    test.equal(actual, expected, [message])
    test.notEqual(actual, expected, [message])
    test.deepEqual(actual, expected, [message])
    test.notDeepEqual(actual, expected, [message])
    test.strictEqual(actual, expected, [message])
    test.notStrictEqual(actual, expected, [message])
    test.throws(block, [error], [message])
    test.doesNotThrow(block, [error], [message])
    test.ifError(value)
*/

exports.compile = {
  setUp: function(done) {
    // setup here
    done();
  },
  'can_bind_variable_to_template': function(test) {
    test.expect(1);
    // tests here
    var template = Webi.compile("<div>{{myVar}}</div>");
    test.equal(template.render({myVar:'test'}), '<div>test</div>', 'should be "test".');
    test.done();
  }
};
